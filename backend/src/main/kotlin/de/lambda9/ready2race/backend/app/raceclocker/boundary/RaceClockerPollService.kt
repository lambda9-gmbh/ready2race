package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamWithRegistration
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchWithTeams
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

    /** Ein Lauf, dessen Turnierstruktur und Mannschaften bereits aufgelöst sind. */
    private data class ResolvedMatch(
        val candidate: RaceClockerPollCandidate,
        val match: CompetitionMatchWithTeams,
        val teams: List<CompetitionMatchTeamWithRegistration>,
    )

    /**
     * Wie das Auflösen eines Laufs ausgegangen ist.
     *
     * [Skip] und [Defect] auf denselben Wert abzubilden wäre die teuerste Vereinfachung dieser
     * Datei: „Der Job lässt den Lauf bewusst in Ruhe" und „es ist etwas unerwartet kaputtgegangen"
     * sehen in der Datenbank sonst gleich aus. Ein Defekt bekäme Takt für Takt einen sauberen
     * Stempel, der Lauf nie ein Ergebnis, und in der Oberfläche stünde nichts — der Fehler wäre nur
     * im Server-Protokoll, wo ihn am Renntag niemand sucht.
     */
    private sealed interface Resolution {
        data class Ok(val match: CompetitionMatchWithTeams) : Resolution

        /** Gesperrt, Freilos, leere Struktur — kein Abruf-Fehler, den die Oberfläche zeigen müsste. */
        data object Skip : Resolution

        data object Defect : Resolution
    }

    /**
     * Was die bisher geholten Rennen über einen Lauf hergeben.
     *
     * Die drei Fälle sind bewusst getrennt, weil sie am Renntag drei verschiedene Dinge bedeuten:
     * gefunden; „die Welle gibt es dort noch nicht" (vor dem Start der Normalfall); und „kein
     * Rennen hat geantwortet" (die einzige echte Störung). Nur der letzte gehört als Fehler in die
     * Oberfläche — eine Warnung, die immer leuchtet, bringt dem Büro bei, auch die eine zu
     * übersehen, auf die es ankommt.
     */
    private sealed interface MatchFeed {
        data class Found(
            /** Alle Zeilen des Rennens — `applyRaceClockerRows` braucht sie, nicht nur die eigenen. */
            val rows: List<RaceClockerFeedRow>,
            val assigned: List<RaceClockerFeedRow>,
        ) : MatchFeed

        data object NotInFeed : MatchFeed

        data class Failed(val errorCode: String?) : MatchFeed
    }

    /**
     * Ein Abruf für eine Veranstaltung, in vier Phasen.
     *
     * Der Umweg über Phasen ist nicht Ordnungsliebe: Der Rückfall soll erst geholt werden, wenn das
     * angewählte Rennen den Lauf nicht enthält — und ob es ihn enthält, weiß man erst, wenn die
     * Mannschaften des Laufs bekannt sind. Auflösen, Zuordnen und Schreiben müssen deshalb
     * auseinander.
     */
    private suspend fun CoroutineComprehensionScope<Nothing>.pollEvent(
        event: RaceClockerPollEvent,
        now: LocalDateTime,
    ) {
        val candidates = !RaceClockerPollRepo.getCandidates(event.eventId).orDie()
        val watched = candidates.filter {
            RaceClockerPollLogic.isWatched(
                activated = it.activatedAt != null,
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

        // Phase 1: Auflösen. `getSetupRoundsWithMatches` sind zwei Abfragen plus der ganze Baum aus
        // Runden, Läufen und Mannschaften. Acht beobachtete Läufe desselben Wettkampfs hätten ihn
        // achtmal je Takt gelesen - einmal je Wettkampf reicht, der Stand kann sich innerhalb eines
        // Taktes nicht ändern.
        val setupRoundsByCompetition = mutableMapOf<UUID, List<CompetitionSetupRoundWithMatches>>()
        // Läufe, die diese Phase aussortiert, brauchen trotzdem ihren Stempel - siehe unten.
        val skipped = mutableListOf<UUID>()
        // Und die, bei denen etwas unerwartet gescheitert ist - die gehören als Fehler sichtbar.
        val defective = mutableListOf<UUID>()
        val resolved = watched.mapNotNull { candidate ->
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

            // Dieselbe Sperre wie beim Knopf: `checkUpdateMatchResult` löst die aktuelle Runde auf
            // und weist einen Lauf außerhalb davon ab. Ohne das würde der Job einen ersten Vorlauf,
            // den niemand beendet hat, für immer weiter beschreiben - und damit Plätze
            // überschreiben, aus denen die Setzung der nächsten Runde längst abgeleitet ist.
            // Scheitert die Prüfung (gesperrt, Freilos, Struktur leer), überspringt der Job den
            // Lauf still: das ist kein Abruf-Fehler, den die Oberfläche anzeigen müsste.
            val resolution = runIsolated<Resolution>(candidate.matchId, Resolution.Defect) {
                CompetitionExecutionService.checkUpdateMatchResult(setupRounds, candidate.matchId)
                    .map { match -> Resolution.Ok(match) as Resolution }
                    .recoverDefault { Resolution.Skip }
            }

            val match = when (resolution) {
                is Resolution.Ok -> resolution.match
                Resolution.Skip -> {
                    skipped += candidate.matchId
                    return@mapNotNull null
                }
                Resolution.Defect -> {
                    defective += candidate.matchId
                    return@mapNotNull null
                }
            }

            ResolvedMatch(candidate, match, match.teams.filter { !it.deregistered })
        }

        // Phase 2: Runde 1 - nur die angewählten Rennen. Ein Abruf liefert das ganze Rennen, deshalb
        // je Adresse genau einmal holen und die Antwort teilen.
        val feeds = mutableMapOf<String, FeedResult>()
        RaceClockerFeedAssignment.primaryUrls(resolved.map { it.candidate.target })
            .forEach { feeds[it] = fetchRows(it) }

        // Über die Lauf-Kennung verschlüsselt, nicht über das Objekt: `ResolvedMatch` trägt den
        // ganzen Lauf mitsamt Mannschaften, und dessen Gleichheit ist hier weder nötig noch billig.
        val firstPass: Map<UUID, MatchFeed> = resolved.associate { entry ->
            // Defekt-Vorgabe ist Failed, nicht NotInFeed: NotInFeed heißt „vor dem Start normal"
            // und würde einen Defekt als gesunden Abruf durchgehen lassen.
            entry.candidate.matchId to runIsolated<MatchFeed>(
                entry.candidate.matchId,
                MatchFeed.Failed(ErrorCode.INTERNAL_ERROR.name),
            ) {
                KIO.ok(assign(entry, feeds))
            }
        }

        // Phase 3: Runde 2 - der Rückfall, aber nur für das, was leer ausgegangen ist. Im gesunden
        // Betrieb ist diese Runde leer, und genau darin liegt die Ersparnis.
        val unresolved = resolved.filter { firstPass[it.candidate.matchId] !is MatchFeed.Found }
        if (unresolved.isNotEmpty()) {
            RaceClockerFeedAssignment
                .fallbackUrls(unresolved.map { it.candidate.target }, feeds.keys.toSet())
                .forEach { feeds[it] = fetchRows(it) }
        }

        // Phase 4: Schreiben.
        //
        // Zuerst die still übersprungenen: Sie bekommen einen Abruf ohne Fehler eingetragen. Das ist
        // keine Kosmetik. Ein Lauf, dessen Runde nicht mehr die aktuelle ist, wird hier absichtlich
        // nicht mehr angefasst - trüge er noch den Fehlercode eines alten Netzaussetzers, bliebe der
        // für immer stehen, weil ihn niemand mehr überschreibt. Durchführungs-Tab und
        // Schiedsrichter-Board zeigten dann dauerhaft eine Störung an einem Lauf, den der Job
        // bewusst in Ruhe lässt - genau die Dauerwarnung, gegen die dieser Job sonst argumentiert.
        skipped.forEach { matchId ->
            runIsolated(matchId, Unit) {
                RaceClockerPollRepo.recordPoll(matchId, now, null).orDie().map { }
            }
        }

        // Die defekten dagegen sichtbar: Sie hätten abgerufen werden sollen.
        defective.forEach { matchId ->
            runIsolated(matchId, Unit) {
                RaceClockerPollRepo.recordPoll(matchId, now, ErrorCode.INTERNAL_ERROR.name).orDie().map { }
            }
        }

        var anyRunning = false
        resolved.forEach { entry ->
            // Wer in Runde 1 gefunden wurde, wird nicht erneut zugeordnet; für alle anderen sind
            // inzwischen die Rückfall-Rennen da.
            val first = firstPass.getValue(entry.candidate.matchId)
            val feed = if (first is MatchFeed.Found) {
                first
            } else {
                runIsolated<MatchFeed>(
                    entry.candidate.matchId,
                    MatchFeed.Failed(ErrorCode.INTERNAL_ERROR.name),
                ) { KIO.ok(assign(entry, feeds)) }
            }

            val outcome = runIsolated(
                entry.candidate.matchId,
                MatchOutcome(errorCode = ErrorCode.INTERNAL_ERROR.name),
            ) {
                writeMatch(entry, feed, now)
            }
            // Der schnelle Takt hängt an der Aktivierung, nicht am Ist-Start: Ein Lauf am Start ist
            // genau der, dessen Startmeldung so früh wie möglich ankommen soll.
            anyRunning = anyRunning || entry.candidate.activatedAt != null || outcome.activated

            runIsolated(entry.candidate.matchId, Unit) {
                RaceClockerPollRepo.recordPoll(entry.candidate.matchId, now, outcome.errorCode).orDie().map { }
            }
        }

        // Der Takt wird erst hier bestimmt, nicht aus der Momentaufnahme von oben: In der Schleife
        // kann ein Lauf aktiviert worden sein, und genau der Takt, der den Start entdeckt, soll
        // schon der schnelle sein. Sonst wartet ein frisch gestarteter Lauf noch einen ganzen
        // langsamen Takt (Vorgabe 60 s) auf seinen ersten Ergebnisabruf - und das Versprechen der
        // Funktion ist, dass Start und Ergebnisse so schnell wie möglich ankommen.
        eventStates[event.eventId] = EventState(now, RaceClockerPollLogic.modeFor(anyRunning))
    }

    /**
     * Sucht diesen Lauf in den bereits geholten Rennen — angewähltes zuerst, dann der Rückfall.
     *
     * Entscheidend ist wie beim Knopf, ob die Welle im Feed STEHT, nicht bloß, ob die Adresse
     * geantwortet hat. Sonst gewönne bei einer als Zeitfahren gefahrenen, aber nicht als
     * Qualifikation markierten Runde immer das erste, falsche Rennen, und der Lauf bliebe die ganze
     * Regatta ohne Ergebnis.
     */
    private fun assign(entry: ResolvedMatch, feeds: Map<String, FeedResult>): MatchFeed {
        val target = entry.candidate.target
        val fetched = target.candidateUrls.mapNotNull { feeds[it] }
        val answered = fetched.filterIsInstance<FeedResult.Rows>()

        val found = answered.firstNotNullOfOrNull { feed ->
            CompetitionExecutionService.assignedRowsFor(feed.rows, entry.teams, target.waveName)
                .takeIf { it.isNotEmpty() }
                ?.let { MatchFeed.Found(feed.rows, it) }
        }
        if (found != null) return found

        // Hat gar kein Rennen mit Zeilen geantwortet, ist DAS der Fehler, den die Oberfläche zeigen
        // soll. Hat eines geantwortet und die Welle fehlt bloß, ist das vor dem Start der Normalfall.
        return if (answered.isEmpty()) {
            MatchFeed.Failed((fetched.firstOrNull() as? FeedResult.Failed)?.errorCode)
        } else {
            MatchFeed.NotInFeed
        }
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
     * Ein einzelner Lauf, ab der fertigen Zuordnung.
     *
     * Ein Fehler bleibt hier: Ein Lauf mit doppelten Crews in RaceClocker darf die anderen Läufe
     * derselben Veranstaltung nicht mitreißen.
     */
    private fun writeMatch(
        entry: ResolvedMatch,
        feed: MatchFeed,
        now: LocalDateTime,
    ): App<Nothing, MatchOutcome> = KIO.comprehension {
        val candidate = entry.candidate
        val match = entry.match

        val found = when (feed) {
            // Gar keine Antwort: Das ist der Fehler, den die Oberfläche zeigen soll.
            is MatchFeed.Failed -> return@comprehension KIO.ok(MatchOutcome(errorCode = feed.errorCode))
            // Eine Welle, die in RaceClocker noch nicht angelegt ist, ist vor dem Start der
            // Normalfall und keine Störung.
            MatchFeed.NotInFeed -> return@comprehension KIO.ok(MatchOutcome())
            is MatchFeed.Found -> feed
        }
        val rows = found.rows
        val assigned = found.assigned

        // Bevorstehender Lauf: nur hinsehen, nichts schreiben außer der Aktivierung. Ein
        // Umsortieren in RaceClocker vor dem Start schlägt erst durch, wenn der Lauf aktiv ist.
        if (candidate.activatedAt == null) {
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

        // Aktiviert, aber noch ohne Ist-Start: Der Lauf wurde von Hand oder von der Kette an den
        // Start gerufen, und der Feed weiß vielleicht schon, dass er losgegangen ist. Der Stempel
        // steht hier und nicht in `applyRaceClockerRows`: Dort kehrt der ergebnislose Lauf zurück,
        // bevor die gemessene Startzeit übernommen wird - seit die Bahnvergabe vorgezogen ist, mit
        // Erfolg statt mit `NoResults`, aber in beiden Fällen ohne Stempel. Und die Funktion läuft
        // innerhalb von `.transact()` - ein dort gesetzter Zeitstempel fiele dem Rollback zum Opfer. Ohne diesen Zweig bliebe ein von
        // der Kette aktivierter Lauf "in Vorbereitung", bis das erste Boot durchs Ziel ist.
        //
        // Welche Zeit das ist, entscheidet [RaceClockerPollLogic.measuredStartFor] - hier steht nur
        // noch das Schreiben. Bewusst ohne `?.let { … }`: Der `!`-Operator der Comprehension
        // funktioniert nur direkt im Block, nicht in einem geschachtelten Lambda.
        val measuredStart = RaceClockerPollLogic.measuredStartFor(
            rows = assigned,
            existingStartedAt = candidate.startedAt,
            plannedStart = candidate.startTime,
            now = now,
        )
        if (measuredStart != null) {
            !CompetitionMatchRepo.update(candidate.matchId) {
                startedAt = measuredStart
                updatedBy = SYSTEM_USER
                updatedAt = now
            }.orDie()
            logger.info { "RaceClocker meldet den Ist-Start von Lauf ${candidate.matchId}." }
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
     * Büro bei, auch die eine zu übersehen, auf die es ankommt.
     *
     * Seit die Bahnvergabe vor dem Ergebnis-Riegel steht, endet dieser Fall in
     * `applyRaceClockerRows` allerdings mit Erfolg statt mit einem Fehler - die übernommenen Bahnen
     * sollen die Transaktion überleben. Der Zweig hier greift deshalb nicht mehr; er bleibt stehen,
     * damit ein wieder eingeführter NoResults-Pfad nicht unbemerkt zur roten Warnung wird.
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
