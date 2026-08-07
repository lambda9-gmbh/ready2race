package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
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

        watched.forEach { candidate ->
            val outcome = pollMatch(candidate, feeds, now)
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
        now: LocalDateTime,
    ): String? {
        // Dieselbe Reihenfolge wie beim Knopf: Die Runde entscheidet, welches Rennen zuerst
        // versucht wird, das andere ist der Rückfall.
        val results = candidate.target.candidateUrls.mapNotNull { feeds[it] }
        val rows = results.firstNotNullOfOrNull { (it as? FeedResult.Rows)?.rows }
            ?: return (results.firstOrNull() as? FeedResult.Failed)?.errorCode

        val match = !CompetitionSetupService.getSetupRoundsWithMatches(candidate.competitionId)
            .map { rounds -> rounds.flatMap { it.matches }.find { it.competitionSetupMatch == candidate.matchId } }
            // Ein Fehler der Turnierstruktur heißt für den Job dasselbe wie "Lauf nicht gefunden":
            // Er überspringt ihn still. `orDie` wäre hier falsch - ein einzelner kaputter Wettkampf
            // würde den ganzen Takt als Defekt beenden.
            .recoverDefault { null }
            ?: return null

        val teams = match.teams.filter { !it.deregistered }
        val assigned = CompetitionExecutionService.assignedRowsFor(rows, teams, candidate.target.waveName)

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

        val errorCode = !CompetitionExecutionService
            .applyRaceClockerRows(match, candidate.matchId, candidate.target, rows, SYSTEM_USER)
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
