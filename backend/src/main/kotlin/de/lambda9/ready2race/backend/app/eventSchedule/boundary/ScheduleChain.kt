package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_SCHEDULE_SLOT
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

data class ChainSlot(
    val slotId: UUID,
    val startTime: LocalDateTime,
    val state: EventScheduleSlotState,
    val matchId: UUID?,
    val matchFinished: Boolean,
    val matchOpen: Boolean,
)

sealed interface ChainDecision {
    data class Activate(val matchIds: List<UUID>) : ChainDecision
    data object WaitingForRound : ChainDecision
    data object NothingToDo : ChainDecision
}

object ScheduleChain {

    /**
     * Wandert die Slots hinter dem beendeten Lauf vorwärts, gruppiert nach Startzeit — parallele
     * Starts gehören zusammen und entscheiden als Einheit, nicht die zufällige Zeilenreihenfolge
     * innerhalb derselben Startzeit. Für jede Gruppe (aufsteigend sortiert):
     * - Enthält sie einen wartenden Slot, stoppt die Suche bewusst OHNE Fehler: Ein paralleler
     *   Start darf nicht halb losgeschickt werden. Die Kette wartet, bis die Runde gesetzt wird —
     *   createNewRound stößt sie dann wieder an (zweiter Auslöser).
     * - Sonst werden alle aktivierbaren Läufe der Gruppe (LINKED, nicht beendet, noch offen)
     *   gemeinsam aktiviert.
     * - Hat die Gruppe nur übersprungene, entfallene, freie oder bereits erledigte/geschlossene
     *   Slots, wird sie übergangen und die nächste Gruppe betrachtet.
     */
    fun decideNext(slotsAfter: List<ChainSlot>): ChainDecision {
        val groups = slotsAfter.groupBy { it.startTime }.toSortedMap()

        for ((_, group) in groups) {
            val hasWaiting = group.any { it.state == EventScheduleSlotState.WAITING }
            if (hasWaiting) {
                return ChainDecision.WaitingForRound
            }

            val activatable = group.filter {
                it.state == EventScheduleSlotState.LINKED && !it.matchFinished && it.matchOpen
            }
            if (activatable.isNotEmpty()) {
                return ChainDecision.Activate(activatable.mapNotNull { it.matchId })
            }
            // Nur FREE/SKIPPED/OBSOLETE bzw. beendete/geschlossene Läufe in dieser Gruppe — weiter.
        }
        return ChainDecision.NothingToDo
    }
}

/**
 * Der Zeitstrahl-Modus der Aktivierungskette (Task 9): [decideAndActivate] ist der Auslöser aus
 * `finishMatch`, [resumeIfParked] der zweite Auslöser aus `createNewRound` und aus
 * `EventScheduleService.setSlotSkipped` — alle teilen sich `buildChainSlots`/
 * `ScheduleChain.decideNext`/die Aktivierung, damit an keiner Stelle zweimal dieselbe Entscheidung
 * anders getroffen wird.
 */
object ScheduleChainService {

    /**
     * Zeitstrahl-Modus von `LiveDashboardService.finishMatch`: [after] ist die Startzeit des Slots
     * des gerade beendeten Laufs.
     */
    fun decideAndActivate(eventId: UUID, after: LocalDateTime, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val chainSlots = !buildChainSlots(eventId, after)
            !activate(ScheduleChain.decideNext(chainSlots), userId)
            KIO.unit
        }

    /**
     * Wieder ansetzen, wenn die Kette an einem wartenden Slot geparkt sein könnte: nach
     * Rundenerzeugung (`createNewRound`) und nach dem Überspringen eines wartenden Slots
     * (`EventScheduleService.setSlotSkipped`). Greift nur, wenn die Automatik an ist und gerade
     * kein Lauf des Events aktiv ist — sonst greift entweder schon ein Lauf, oder die Veranstaltung
     * will die Kette gar nicht.
     */
    fun resumeIfParked(eventId: UUID, userId: UUID): App<Nothing, Unit> = KIO.comprehension {
        val chainEnabled = !EventRepo.getAutoActivateNextMatch(eventId).orDie()
        if (!chainEnabled) {
            return@comprehension KIO.unit
        }

        val alreadyRunning = !EventScheduleRepo.hasRunningMatch(eventId).orDie()
        if (alreadyRunning) {
            return@comprehension KIO.unit
        }

        // Resume greift erst, wenn schon ein verplanter Lauf beendet wurde — den allerersten Lauf
        // aktiviert der Schiedsrichter wie bisher von Hand. Ohne Referenzpunkt gibt es also nichts
        // zu tun (und insbesondere keinen Fallback auf LocalDateTime.MIN, der beim jOOQ-Bind auf
        // Timestamp.valueOf() mit "timestamp out of range" gegen Postgres krachen würde).
        val reference = !EventScheduleRepo.getLastFinishedSlotTime(eventId).orDie()
        if (reference == null) {
            return@comprehension KIO.unit
        }

        val chainSlots = !buildChainSlots(eventId, reference)
        !activate(ScheduleChain.decideNext(chainSlots), userId)

        KIO.unit
    }

    /** Liest die Slots nach [after] und leitet ihren Zustand wie in [EventScheduleService] ab. */
    private fun buildChainSlots(eventId: UUID, after: LocalDateTime): App<Nothing, List<ChainSlot>> =
        KIO.comprehension {
            val records = !EventScheduleRepo.getChainSlots(eventId, after).orDie()
            KIO.ok(records.map { r ->
                val isFree = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
                val matchExists = r.get("match_exists", Boolean::class.java) == true
                ChainSlot(
                    slotId = r[EVENT_SCHEDULE_SLOT.ID]!!,
                    startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                    state = EventScheduleLogic.deriveSlotState(
                        isFree = isFree,
                        skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                        roundMaterialized = r.get("round_materialized", Boolean::class.java) == true,
                        matchExists = matchExists,
                    ),
                    matchId = if (matchExists) r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] else null,
                    matchFinished = r.get("match_finished_at", LocalDateTime::class.java) != null,
                    matchOpen = r.get("match_open", Boolean::class.java) == true,
                )
            })
        }

    private fun activate(decision: ChainDecision, userId: UUID): App<Nothing, Unit> = when (decision) {
        is ChainDecision.Activate -> decision.matchIds.traverse { setRunning(it, userId) }.map { }
        ChainDecision.WaitingForRound, ChainDecision.NothingToDo -> KIO.unit
    }

    private fun setRunning(matchId: UUID, userId: UUID): App<Nothing, Unit> =
        CompetitionMatchRepo.update(matchId) {
            currentlyRunning = true
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().map { }
}
