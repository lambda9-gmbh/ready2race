package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic.PollMode
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerPollRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollCandidate
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollEvent
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.kio.CoroutineComprehensionScope
import de.lambda9.ready2race.backend.kio.comprehension
import de.lambda9.ready2race.backend.kio.fold
import de.lambda9.ready2race.backend.schedule.DynamicIntervalJobState
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import de.lambda9.tailwind.jooq.transact
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Der automatische RaceClocker-Abruf (Entwurf 2026-08-07).
 *
 * [pollTick] ist ein Herzschlag, kein Abruf: Er läuft im Sekundentakt und entscheidet je
 * Veranstaltung, ob ihr eingestellter Takt fällig ist. Der Umweg ist nötig, weil die Takte pro
 * Veranstaltung einstellbar sind - ein fest verdrahteter 5-Sekunden-Job könnte einen auf 3 Sekunden
 * gestellten Takt nicht bedienen, und ein Job je Veranstaltung wäre eine Job-Verwaltung, die es
 * hier nicht braucht.
 *
 * Der Job beendet nie einen Lauf (Entscheidung vom 04.08.2026) und schreibt an bevorstehenden
 * Läufen nichts außer der Aktivierung.
 */
object RaceClockerPollService {

    private val logger = KotlinLogging.logger {}

    private data class EventState(val lastPolledAt: LocalDateTime, val mode: PollMode)

    /**
     * Wann eine Veranstaltung zuletzt abgerufen wurde und in welchem Takt sie dabei lief. Im
     * Speicher statt in der Datenbank: Nach einem Neustart wird einmal sofort abgerufen, was
     * harmlos ist - der Wert interessiert niemanden außerhalb dieses Jobs.
     */
    private val eventStates = ConcurrentHashMap<UUID, EventState>()

    /**
     * Der zuletzt geschriebene Stand je Lauf. Ist er unverändert, schreibt der Job nichts - sonst
     * sähe jeder aktive Lauf alle fünf Sekunden "bearbeitet" aus.
     */
    private val fingerprints = ConcurrentHashMap<UUID, String>()

    /**
     * Vergisst den zuletzt geschriebenen Stand eines Laufs.
     *
     * Nötig beim Freigeben eines pausierten Laufs: Während der Pause hat jemand von Hand
     * eingetragen, der Fingerabdruck im Speicher beschreibt aber weiterhin den Stand von davor.
     * Ohne dieses Vergessen liefe der nächste Takt in die Abkürzung "unverändert, nichts
     * schreiben" - der Bediener drückt "Automatik wieder aufnehmen", die Oberfläche meldet einen
     * gesunden Abruf, und RaceClocker übernimmt den Lauf trotzdem erst, wenn sich dort
     * irgendwann eine Zeile ändert. Genau dann käme die Überschreibung unangekündigt.
     */
    fun forget(matchId: UUID) {
        fingerprints.remove(matchId)
    }

    suspend fun pollTick(env: JEnv): App<Nothing, DynamicIntervalJobState> = coroutineScope {
        comprehension(env) {
            val events = !RaceClockerPollRepo.getPollingEvents().orDie()

            // Keine Veranstaltung mit Automatik: lange Pause. Sobald eine eingeschaltet ist, läuft
            // der Herzschlag im Sekundentakt - die Abfrage darüber ist genau dafür so klein.
            if (events.isEmpty()) {
                eventStates.clear()
                fingerprints.clear()
                return@comprehension KIO.ok(DynamicIntervalJobState.Empty)
            }

            val now = LocalDateTime.now()
            events.forEach { event ->
                val state = eventStates[event.eventId]
                val interval = RaceClockerPollLogic.intervalSeconds(
                    when (state?.mode) {
                        PollMode.ACTIVE -> event.intervalActiveSeconds
                        else -> event.intervalUpcomingSeconds
                    }
                )
                if (RaceClockerPollLogic.isDue(state?.lastPolledAt, now, interval)) {
                    pollEvent(event, now)
                }
            }

            KIO.ok(DynamicIntervalJobState.Processed)
        }
    }

    /**
     * Ein Abruf für eine Veranstaltung: beobachtete Läufe bestimmen, jede benötigte Adresse genau
     * einmal holen, die Antwort auf die Läufe verteilen.
     */
    private suspend fun CoroutineComprehensionScope<Nothing>.pollEvent(
        event: RaceClockerPollEvent,
        now: LocalDateTime,
    ) {
        val candidates = !RaceClockerPollRepo.getCandidates(event.eventId).orDie()
        val watched = candidates.filter {
            RaceClockerPollLogic.isWatched(
                currentlyRunning = it.currentlyRunning,
                startTime = it.startTime,
                now = now,
                watchBeforeMinutes = event.watchBeforeMinutes,
                watchAfterMinutes = event.watchAfterMinutes,
            )
        }

        if (watched.isEmpty()) {
            eventStates[event.eventId] = EventState(now, RaceClockerPollLogic.modeFor(anyRunning = false))
            return
        }

        // Ein Abruf liefert das ganze Rennen. Deshalb je Adresse genau einmal holen und die Antwort
        // teilen - bei einer Regatta sind das ein bis zwei Abrufe pro Takt, egal wie viele Läufe
        // gerade laufen.
        val feeds = watched.flatMap { it.target.candidateUrls }.distinct()
            .associateWith { fetchRows(it) }

        // Dieselbe Sparsamkeit auf der Datenbankseite: `getSetupRoundsWithMatches` sind zwei
        // Abfragen plus der ganze Baum aus Runden, Läufen und Mannschaften. Acht beobachtete Läufe
        // desselben Wettkampfs hätten ihn achtmal je Takt gelesen - einmal je Wettkampf reicht, der
        // Stand kann sich innerhalb eines Taktes nicht ändern.
        val setupRoundsByCompetition = mutableMapOf<UUID, List<CompetitionSetupRoundWithMatches>>()

        var anyRunning = false
        watched.forEach { candidate ->
            val setupRounds = setupRoundsByCompetition.getOrPut(candidate.competitionId) {
                // Ein Fehler der Turnierstruktur heißt für den Job dasselbe wie "Lauf nicht
                // gefunden": Er überspringt ihn still. `orDie` wäre hier falsch - ein einzelner
                // kaputter Wettkampf würde den ganzen Takt als Defekt beenden. Dasselbe gilt für
                // einen Defekt darin, deshalb zusätzlich [runIsolated]: `recoverDefault` fängt nur
                // typisierte Fehler.
                runIsolated(candidate.matchId, emptyList()) {
                    CompetitionSetupService.getSetupRoundsWithMatches(candidate.competitionId)
                        .recoverDefault { emptyList() }
                }
            }
            val outcome = runIsolated(candidate.matchId, MatchOutcome(errorCode = ErrorCode.INTERNAL_ERROR.name)) {
                pollMatch(candidate, feeds, setupRounds, now)
            }
            anyRunning = anyRunning || candidate.currentlyRunning || outcome.activated

            runIsolated(candidate.matchId, Unit) {
                RaceClockerPollRepo.recordPoll(candidate.matchId, now, outcome.errorCode).orDie().map { }
            }
        }

        // Der Takt wird erst hier bestimmt, nicht aus der Momentaufnahme von oben: In der Schleife
        // kann ein Lauf aktiviert worden sein, und genau der Takt, der den Start entdeckt, soll
        // schon der schnelle sein. Sonst wartet ein frisch gestarteter Lauf noch einen ganzen
        // langsamen Takt (Vorgabe 60 s) auf seinen ersten Ergebnisabruf - und das Versprechen der
        // Funktion ist, dass Start und Ergebnisse so schnell wie möglich ankommen.
        eventStates[event.eventId] = EventState(now, RaceClockerPollLogic.modeFor(anyRunning))
    }

    /** Was ein Abrufversuch über einen Lauf ergeben hat. */
    private data class MatchOutcome(
        /** Der ErrorCode des Fehlschlags oder null. */
        val errorCode: String? = null,
        /** Ob dieser Takt den Lauf aktiviert hat - er zählt dann schon als laufend. */
        val activated: Boolean = false,
    )

    /**
     * Führt einen Schritt eines einzelnen Laufs so aus, dass ein Defekt nur diesen Lauf trifft.
     *
     * `CoroutineComprehensionScope.not()` wirft nicht nur typisierte Fehler, sondern auch Defekte
     * nach oben - jedes `orDie` in `applyRaceClockerRows`, `recordPoll` oder `CompetitionMatchRepo`
     * würde sonst aus [pollMatch] über die Schleife bis in [pollTick] durchschlagen und alle noch
     * nicht besuchten Läufe dieses Takts mitreißen. Ein reproduzierbarer Defekt (etwa ein
     * Lock-Timeout auf einem Lauf, den jemand offen hat) ließe die späteren Läufe damit dauerhaft
     * verhungern, ohne dass es irgendwo sichtbar wäre. Dieselbe Isolation, die [fetchRows] für
     * HTTP-Defekte längst leistet.
     */
    private fun <A> CoroutineComprehensionScope<Nothing>.runIsolated(
        matchId: UUID,
        onDefect: A,
        block: () -> App<Nothing, A>,
    ): A = block().unsafeRunSync(env).fold(
        onSuccess = { it },
        onDefect = {
            logger.error(it) { "RaceClocker-Abruf für Lauf $matchId ist unerwartet gescheitert." }
            onDefect
        },
    )

    /**
     * Ein einzelner Lauf.
     *
     * Ein Fehler bleibt hier: Ein Lauf mit doppelten Crews in RaceClocker darf die anderen Läufe
     * derselben Veranstaltung nicht mitreißen.
     */
    private fun pollMatch(
        candidate: RaceClockerPollCandidate,
        feeds: Map<String, FeedResult>,
        setupRounds: List<CompetitionSetupRoundWithMatches>,
        now: LocalDateTime,
    ): App<Nothing, MatchOutcome> = KIO.comprehension {
        // Dieselbe Sperre wie beim Knopf, und aus demselben Grund: `checkUpdateMatchResult` löst die
        // aktuelle Runde auf und weist einen Lauf außerhalb davon ab. Ohne das würde der Job einen
        // ersten Vorlauf, den niemand beendet hat, für immer weiter beschreiben - und damit Plätze
        // überschreiben, aus denen die Setzung der nächsten Runde längst abgeleitet ist. Scheitert
        // die Prüfung (gesperrt, Freilos, Struktur leer), überspringt der Job den Lauf still: das
        // ist kein Abruf-Fehler, den die Oberfläche anzeigen müsste.
        val match = !CompetitionExecutionService.checkUpdateMatchResult(setupRounds, candidate.matchId)
            .recoverDefault { null }
            ?: return@comprehension KIO.ok(MatchOutcome())

        val teams = match.teams.filter { !it.deregistered }

        // Dieselbe Reihenfolge wie beim Knopf: Die Runde entscheidet, welches Rennen zuerst versucht
        // wird, das andere ist der Rückfall. Entscheidend ist wie dort, ob die Welle im Feed steht -
        // nicht bloß, ob die Adresse geantwortet hat. Sonst gewönne bei einer als Zeitfahren
        // gefahrenen, aber nicht als Qualifikation markierten Runde immer das erste, falsche Rennen,
        // und der Lauf bliebe die ganze Regatta ohne Ergebnis. Ein weiterer Abruf kostet das nicht:
        // Beide Adressen liegen bereits geholt in [feeds].
        val fetched = candidate.target.candidateUrls.mapNotNull { feeds[it] }
        val answered = fetched.filterIsInstance<FeedResult.Rows>()
        val (rows, assigned) = answered
            .firstNotNullOfOrNull { feed ->
                CompetitionExecutionService.assignedRowsFor(feed.rows, teams, candidate.target.waveName)
                    .takeIf { it.isNotEmpty() }
                    ?.let { feed.rows to it }
            }
            // Keine Adresse kennt die Welle: Es bleibt beim ersten geholten Rennen, damit der Lauf
            // unten auf demselben Weg endet wie bisher - kein Treffer, kein Fehler.
            ?: answered.firstOrNull()?.let { it.rows to emptyList<RaceClockerFeedRow>() }
            // Gar keine Antwort: Das ist der Fehler, den die Oberfläche zeigen soll.
            ?: return@comprehension KIO.ok(
                MatchOutcome(errorCode = (fetched.firstOrNull() as? FeedResult.Failed)?.errorCode)
            )

        // Eine Welle, die in RaceClocker noch nicht angelegt ist, ist vor dem Start der Normalfall
        // und keine Störung.
        if (assigned.isEmpty()) return@comprehension KIO.ok(MatchOutcome())

        // Bevorstehender Lauf: nur hinsehen, nichts schreiben außer der Aktivierung. Ein
        // Umsortieren in RaceClocker vor dem Start schlägt erst durch, wenn der Lauf aktiv ist.
        if (!candidate.currentlyRunning) {
            if (!RaceClockerPollLogic.startDetected(assigned)) return@comprehension KIO.ok(MatchOutcome())

            !CompetitionMatchRepo.update(candidate.matchId) {
                if (activatedAt == null) {
                    activatedAt = now
                }
                if (startedAt == null) {
                    startedAt = now
                }
                updatedBy = SYSTEM_USER
                updatedAt = now
            }.orDie()
            logger.info { "RaceClocker meldet den Start von Lauf ${candidate.matchId} - Lauf aktiviert." }
            return@comprehension KIO.ok(MatchOutcome(activated = true))
        }

        // Unverändert seit dem letzten Abruf: nichts schreiben.
        val fingerprint = RaceClockerPollLogic.fingerprint(assigned)
        if (fingerprints[candidate.matchId] == fingerprint) return@comprehension KIO.ok(MatchOutcome())

        // `transact()`, weil der Job im Gegensatz zum Endpunkt keine mitgebrachte Transaktion hat:
        // `respondKIO` legt eine um den ganzen Aufruf, hier läuft jedes `!` für sich. Ohne die
        // Klammer bliebe ein Lauf halb geschrieben zurück, sobald `applyRaceClockerRows` nach den
        // ersten Schreibvorgängen scheitert - etwa mit MatchTeamNotFound, wenn die Plätze schon
        // gelöscht und die Bahnen schon neu gesetzt sind. Und weil die Bahnvergabe die
        // Startnummern zwischendurch negiert, sähe das Live-Dashboard sonst kurzzeitig einen Lauf
        // mit lauter negativen Bahnen.
        val write = !KIO.comprehension<JEnv, ServiceError, WriteOutcome> {
            // Die Pause wird hier ein zweites Mal geprüft, in derselben Transaktion wie das
            // Schreiben. `getCandidates` hat sie am Anfang des Takts gelesen, dazwischen liegen bis
            // zu zwei HTTP-Abrufe mit je 10 s Zeitlimit. Trägt ein Schiedsrichter in dieser Lücke
            // von Hand ein, sieht der Job die Pause nicht und schriebe seinen Stand darüber - der
            // Eintrag wäre weg, ab dem nächsten Takt gilt der Lauf als pausiert und wird nie wieder
            // angefasst, und die Oberfläche meldet "pausiert", was sich liest wie "mein Eintrag
            // steht".
            val paused = !RaceClockerPollRepo.isAutoPaused(candidate.matchId).orDie()
            if (paused) return@comprehension KIO.ok(WriteOutcome(rememberFingerprint = false))

            CompetitionExecutionService
                .applyRaceClockerRows(match, candidate.matchId, candidate.target, rows, SYSTEM_USER)
                .map { WriteOutcome(rememberFingerprint = true) }
        }.transact().recoverDefault { error -> failedWrite(candidate.matchId, error) }

        if (write.rememberFingerprint) {
            fingerprints[candidate.matchId] = fingerprint
        }
        KIO.ok(MatchOutcome(errorCode = write.errorCode))
    }

    private data class WriteOutcome(
        val errorCode: String? = null,
        /**
         * Ob der Fingerabdruck jetzt den Stand in der Datenbank beschreibt. Nur dann darf er
         * gemerkt werden - sonst überspränge der nächste Takt eine Änderung, die nie ankam.
         */
        val rememberFingerprint: Boolean,
    )

    /**
     * Wie ein gescheiterter Schreibversuch zu bewerten ist.
     *
     * [RaceClockerError.NoResults] ist wie ein fehlender Treffer der Normalfall und keine Störung:
     * Solange in einem Lauf jedes Boot noch `In race…` zeigt, sind die Zeilen zwar zugeordnet, aber
     * keine trägt ein verwertbares Ergebnis. Das ist der Zustand fast jedes Laufs über fast seine
     * ganze Dauer. Als Fehlercode gespeichert stünde in Durchführungs-Tab und Live-Dashboard bei
     * jedem laufenden Lauf eine orange Warnung - und eine Warnung, die immer leuchtet, bringt dem
     * Büro bei, auch die eine zu übersehen, auf die es ankommt. Geschrieben wurde dabei nichts
     * (die Prüfung steht in `applyRaceClockerRows` vor allen Schreibvorgängen), der Fingerabdruck
     * darf also gemerkt werden und erspart den erfolglosen Versuch im nächsten Takt.
     *
     * Alles andere wandert als Code in die Spalte. Fehler außerhalb von [RaceClockerError] haben im
     * Frontend keine eigene Übersetzung und erscheinen dort als "Unerwarteter Fehler" - deshalb
     * werden sie zusätzlich hier protokolliert, sonst ließe sich am Renntag nicht feststellen,
     * woran es lag.
     */
    private fun failedWrite(matchId: UUID, error: ServiceError): WriteOutcome {
        if (error is RaceClockerError.NoResults) return WriteOutcome(rememberFingerprint = true)

        val errorCode = error.respond().errorCode?.name
        if (error !is RaceClockerError) {
            logger.warn { "RaceClocker-Abruf für Lauf $matchId scheiterte an $errorCode ($error)." }
        }
        return WriteOutcome(errorCode = errorCode, rememberFingerprint = false)
    }

    private sealed interface FeedResult {
        data class Rows(val rows: List<RaceClockerFeedRow>) : FeedResult
        data class Failed(val errorCode: String?) : FeedResult
    }

    /**
     * Holt einen Feed und fängt seinen Fehler ab, statt den Takt scheitern zu lassen. Die
     * Fehlermeldung wird auf ihren ErrorCode eingedampft, damit die Oberfläche sie übersetzen kann
     * (siehe `raceClockerErrorText` im Frontend).
     */
    private suspend fun fetchRows(rawUrl: String): FeedResult {
        val url = RaceClockerFeed.normalizeUrl(rawUrl).unsafeRunSync().getOrNull()
            ?: return FeedResult.Failed(ErrorCode.RACECLOCKER_URL_INVALID.name)

        return RaceClockerFeed.fetch(RaceClockerFeed.feedUrl(url)).unsafeRunSync().fold(
            onSuccess = { FeedResult.Rows(it) },
            onError = { FeedResult.Failed(it.respond().errorCode?.name) },
            onDefect = {
                logger.warn(it) { "RaceClocker-Abruf von $rawUrl ist unerwartet gescheitert." }
                FeedResult.Failed(ErrorCode.RACECLOCKER_UNREACHABLE.name)
            },
        )
    }
}
