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
     * Wandert die Slots hinter dem beendeten Lauf vorwärts. Übersprungene, entfallene und freie
     * Slots sowie beendete/abgeschlossene Läufe werden übergangen. Ein wartender Slot stoppt die
     * Suche bewusst OHNE Fehler: Die Kette wartet, bis die Runde gesetzt wird — createNewRound
     * stößt sie dann wieder an (zweiter Auslöser).
     */
    fun decideNext(slotsAfter: List<ChainSlot>): ChainDecision {
        for (slot in slotsAfter) {
            when (slot.state) {
                EventScheduleSlotState.SKIPPED,
                EventScheduleSlotState.OBSOLETE,
                EventScheduleSlotState.FREE -> continue

                EventScheduleSlotState.WAITING -> return ChainDecision.WaitingForRound

                EventScheduleSlotState.LINKED -> {
                    if (slot.matchFinished || !slot.matchOpen) continue
                    // Parallele Starts: alle aktivierbaren Läufe derselben Startzeit gemeinsam.
                    val group = slotsAfter.filter {
                        it.startTime == slot.startTime &&
                            it.state == EventScheduleSlotState.LINKED &&
                            !it.matchFinished && it.matchOpen
                    }
                    return ChainDecision.Activate(group.mapNotNull { it.matchId })
                }
            }
        }
        return ChainDecision.NothingToDo
    }
}

/**
 * Der Zeitstrahl-Modus der Aktivierungskette (Task 9): [decideAndActivate] ist der Auslöser aus
 * `finishMatch`, [resumeAfterRoundCreation] der zweite Auslöser aus `createNewRound` — beide teilen
 * sich `buildChainSlots`/`ScheduleChain.decideNext`/die Aktivierung, damit an keiner Stelle zweimal
 * dieselbe Entscheidung anders getroffen wird.
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
     * Zweiter Auslöser des wartenden Breakpoints: nach Rundenerzeugung wieder ansetzen, aber nur
     * wenn die Automatik an ist und gerade kein Lauf des Events aktiv ist — sonst greift entweder
     * schon ein Lauf, oder die Veranstaltung will die Kette gar nicht.
     */
    fun resumeAfterRoundCreation(eventId: UUID, userId: UUID): App<Nothing, Unit> = KIO.comprehension {
        val chainEnabled = !EventRepo.getAutoActivateNextMatch(eventId).orDie()
        if (!chainEnabled) {
            return@comprehension KIO.unit
        }

        val alreadyRunning = !EventScheduleRepo.hasRunningMatch(eventId).orDie()
        if (alreadyRunning) {
            return@comprehension KIO.unit
        }

        val reference = !EventScheduleRepo.getLastFinishedSlotTime(eventId).orDie()
        val chainSlots = !buildChainSlots(eventId, reference ?: LocalDateTime.MIN)
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
