package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic.PollMode
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerPollRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollCandidate
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollEvent
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.kio.CoroutineComprehensionScope
import de.lambda9.ready2race.backend.kio.comprehension
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

        eventStates[event.eventId] = EventState(now, RaceClockerPollLogic.modeFor(watched.any { it.currentlyRunning }))
        if (watched.isEmpty()) return

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

        watched.forEach { candidate ->
            val setupRounds = setupRoundsByCompetition.getOrPut(candidate.competitionId) {
                // Ein Fehler der Turnierstruktur heißt für den Job dasselbe wie "Lauf nicht
                // gefunden": Er überspringt ihn still. `orDie` wäre hier falsch - ein einzelner
                // kaputter Wettkampf würde den ganzen Takt als Defekt beenden.
                !CompetitionSetupService.getSetupRoundsWithMatches(candidate.competitionId)
                    .recoverDefault { emptyList() }
            }
            val outcome = pollMatch(candidate, feeds, setupRounds, now)
            !RaceClockerPollRepo.recordPoll(candidate.matchId, now, outcome).orDie()
        }
    }

    /**
     * Ein einzelner Lauf. Liefert den ErrorCode des Fehlschlags oder null.
     *
     * Ein Fehler bleibt hier: Ein Lauf mit doppelten Crews in RaceClocker darf die anderen Läufe
     * derselben Veranstaltung nicht mitreißen.
     */
    private fun CoroutineComprehensionScope<Nothing>.pollMatch(
        candidate: RaceClockerPollCandidate,
        feeds: Map<String, FeedResult>,
        setupRounds: List<CompetitionSetupRoundWithMatches>,
        now: LocalDateTime,
    ): String? {
        // Dieselbe Sperre wie beim Knopf, und aus demselben Grund: `checkUpdateMatchResult` löst die
        // aktuelle Runde auf und weist einen Lauf außerhalb davon ab. Ohne das würde der Job einen
        // ersten Vorlauf, den niemand beendet hat, für immer weiter beschreiben - und damit Plätze
        // überschreiben, aus denen die Setzung der nächsten Runde längst abgeleitet ist. Scheitert
        // die Prüfung (gesperrt, Freilos, Struktur leer), überspringt der Job den Lauf still: das
        // ist kein Abruf-Fehler, den die Oberfläche anzeigen müsste.
        val match = !CompetitionExecutionService.checkUpdateMatchResult(setupRounds, candidate.matchId)
            .recoverDefault { null }
            ?: return null

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
            ?: return (fetched.firstOrNull() as? FeedResult.Failed)?.errorCode

        // Eine Welle, die in RaceClocker noch nicht angelegt ist, ist vor dem Start der Normalfall
        // und keine Störung.
        if (assigned.isEmpty()) return null

        // Bevorstehender Lauf: nur hinsehen, nichts schreiben außer der Aktivierung. Ein
        // Umsortieren in RaceClocker vor dem Start schlägt erst durch, wenn der Lauf aktiv ist.
        if (!candidate.currentlyRunning) {
            if (RaceClockerPollLogic.startDetected(assigned)) {
                !CompetitionMatchRepo.update(candidate.matchId) {
                    currentlyRunning = true
                    if (startedAt == null) {
                        startedAt = now
                    }
                    updatedBy = SYSTEM_USER
                    updatedAt = now
                }.orDie()
                logger.info { "RaceClocker meldet den Start von Lauf ${candidate.matchId} - Lauf aktiviert." }
            }
            return null
        }

        // Unverändert seit dem letzten Abruf: nichts schreiben.
        val fingerprint = RaceClockerPollLogic.fingerprint(assigned)
        if (fingerprints[candidate.matchId] == fingerprint) return null

        // `transact()`, weil der Job im Gegensatz zum Endpunkt keine mitgebrachte Transaktion hat:
        // `respondKIO` legt eine um den ganzen Aufruf, hier läuft jedes `!` für sich. Ohne die
        // Klammer bliebe ein Lauf halb geschrieben zurück, sobald `applyRaceClockerRows` nach den
        // ersten Schreibvorgängen scheitert - etwa mit MatchTeamNotFound, wenn die Plätze schon
        // gelöscht und die Bahnen schon neu gesetzt sind. Und weil die Bahnvergabe die
        // Startnummern zwischendurch negiert, sähe das Live-Dashboard sonst kurzzeitig einen Lauf
        // mit lauter negativen Bahnen.
        val errorCode = !CompetitionExecutionService
            .applyRaceClockerRows(match, candidate.matchId, candidate.target, rows, SYSTEM_USER)
            .transact()
            .map { null as String? }
            .recoverDefault { error -> error.respond().errorCode?.name }

        if (errorCode == null) {
            fingerprints[candidate.matchId] = fingerprint
        }
        return errorCode
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
            ?: return FeedResult.Failed("RACECLOCKER_URL_INVALID")

        return RaceClockerFeed.fetch(RaceClockerFeed.feedUrl(url)).unsafeRunSync().fold(
            onSuccess = { FeedResult.Rows(it) },
            onError = { FeedResult.Failed(it.respond().errorCode?.name) },
            onDefect = {
                logger.warn(it) { "RaceClocker-Abruf von $rawUrl ist unerwartet gescheitert." }
                FeedResult.Failed("RACECLOCKER_UNREACHABLE")
            },
        )
    }
}
