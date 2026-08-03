package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ChainDecision
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ChainSlot
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleChain
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState.*
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScheduleChainTest {

    private val base = LocalDateTime.of(2026, 8, 17, 10, 0)
    private fun slot(
        min: Long,
        state: de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState,
        matchId: UUID? = null,
        finished: Boolean = false,
        open: Boolean = true,
    ) = ChainSlot(UUID.randomUUID(), base.plusMinutes(min), state, matchId, finished, open)

    @Test
    fun activatesTheNextLinkedOpenMatch() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(listOf(slot(10, LINKED, m)))
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun waitsAtAWaitingSlotInsteadOfSkippingAhead() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(slot(10, WAITING), slot(20, LINKED, m)),
        )
        assertIs<ChainDecision.WaitingForRound>(decision)
    }

    @Test
    fun skippedObsoleteAndFreeSlotsArePassedOver() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(slot(10, SKIPPED), slot(20, OBSOLETE), slot(30, FREE), slot(40, LINKED, m)),
        )
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun finishedOrClosedMatchesArePassedOver() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(
                slot(10, LINKED, UUID.randomUUID(), finished = true),
                slot(20, LINKED, UUID.randomUUID(), open = false),
                slot(30, LINKED, m),
            ),
        )
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun parallelStartsActivateTogether() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val s1 = slot(10, LINKED, a)
        val s2 = ChainSlot(UUID.randomUUID(), s1.startTime, LINKED, b, matchFinished = false, matchOpen = true)
        assertEquals(ChainDecision.Activate(listOf(a, b)), ScheduleChain.decideNext(listOf(s1, s2)))
    }

    @Test
    fun emptyTailMeansNothingToDo() {
        assertIs<ChainDecision.NothingToDo>(ScheduleChain.decideNext(emptyList()))
    }

    @Test
    fun mixedGroupWithWaitingSlotWaitsAsAUnit() {
        val m = UUID.randomUUID()
        val t = base.plusMinutes(10)
        val linked = ChainSlot(UUID.randomUUID(), t, LINKED, m, matchFinished = false, matchOpen = true)
        val waiting = ChainSlot(UUID.randomUUID(), t, WAITING, null, matchFinished = false, matchOpen = false)
        assertIs<ChainDecision.WaitingForRound>(ScheduleChain.decideNext(listOf(linked, waiting)))
    }

    @Test
    fun groupWithOnlyFinishedMatchesIsPassedOver() {
        val m = UUID.randomUUID()
        val t = base.plusMinutes(10)
        val done = ChainSlot(UUID.randomUUID(), t, LINKED, UUID.randomUUID(), matchFinished = true, matchOpen = false)
        val next = ChainSlot(UUID.randomUUID(), base.plusMinutes(20), LINKED, m, matchFinished = false, matchOpen = true)
        assertEquals(ChainDecision.Activate(listOf(m)), ScheduleChain.decideNext(listOf(done, next)))
    }
}
