package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleLogic
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ShiftResult
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ShiftSlot
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventScheduleLogicTest {

    // --- deriveSlotState ---

    @Test
    fun freeSlotIsFree() {
        assertEquals(
            EventScheduleSlotState.FREE,
            EventScheduleLogic.deriveSlotState(isFree = true, skipped = false, roundMaterialized = false, matchExists = false),
        )
    }

    @Test
    fun setupSlotWithoutMatchIsWaiting() {
        assertEquals(
            EventScheduleSlotState.WAITING,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = false, roundMaterialized = false, matchExists = false),
        )
    }

    @Test
    fun setupSlotWithMatchIsLinked() {
        assertEquals(
            EventScheduleSlotState.LINKED,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = false, roundMaterialized = true, matchExists = true),
        )
    }

    @Test
    fun materializedRoundWithoutMatchIsObsolete() {
        assertEquals(
            EventScheduleSlotState.OBSOLETE,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = false, roundMaterialized = true, matchExists = false),
        )
    }

    @Test
    fun obsoleteTrumpsSkipped() {
        assertEquals(
            EventScheduleSlotState.OBSOLETE,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = true, roundMaterialized = true, matchExists = false),
        )
    }

    @Test
    fun skippedTrumpsWaitingLinkedAndFree() {
        assertEquals(
            EventScheduleSlotState.SKIPPED,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = true, roundMaterialized = false, matchExists = false),
        )
        assertEquals(
            EventScheduleSlotState.SKIPPED,
            EventScheduleLogic.deriveSlotState(isFree = false, skipped = true, roundMaterialized = true, matchExists = true),
        )
        assertEquals(
            EventScheduleSlotState.SKIPPED,
            EventScheduleLogic.deriveSlotState(isFree = true, skipped = true, roundMaterialized = false, matchExists = false),
        )
    }

    // --- computeShift ---

    private val base = LocalDateTime.of(2026, 8, 17, 10, 0)
    private fun slot(minutesAfterBase: Long, duration: Int? = null) =
        ShiftSlot(UUID.randomUUID(), base.plusMinutes(minutesAfterBase), duration)

    @Test
    fun plainShiftMovesEverySlot() {
        val slots = listOf(slot(0), slot(10), slot(20))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 15, targetSlotId = null)
        val ok = assertIs<ShiftResult.Ok>(result)
        assertEquals(listOf(15L, 25L, 35L), ok.entries.map {
            java.time.Duration.between(base, it.newStartTime).toMinutes()
        })
    }

    @Test
    fun compressKeepsTargetTimeAndShrinksGaps() {
        // 10:00, 10:10, 10:20, 10:30 — +6 min, Ziel = letzter Slot.
        // 6 Minuten müssen aus den drei 10er-Abständen (Untergrenze 5) heraus: je -2.
        val slots = listOf(slot(0), slot(10), slot(20), slot(30))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 6, targetSlotId = slots[3].id)
        val ok = assertIs<ShiftResult.Ok>(result)
        assertEquals(listOf(6L, 14L, 22L, 30L), ok.entries.map {
            java.time.Duration.between(base, it.newStartTime).toMinutes()
        })
    }

    @Test
    fun slotsAfterTargetStayUntouched() {
        val slots = listOf(slot(0), slot(10), slot(20))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 4, targetSlotId = slots[1].id)
        val ok = assertIs<ShiftResult.Ok>(result)
        assertEquals(listOf(4L, 10L, 20L), ok.entries.map {
            java.time.Duration.between(base, it.newStartTime).toMinutes()
        })
    }

    @Test
    fun durationRaisesTheFloorOfAGap() {
        // Abstand 10, Dauer des vorderen Slots 8 → nur 2 Minuten Spielraum.
        val slots = listOf(slot(0, duration = 8), slot(10))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 2, targetSlotId = slots[1].id)
        val ok = assertIs<ShiftResult.Ok>(result)
        assertEquals(listOf(2L, 10L), ok.entries.map {
            java.time.Duration.between(base, it.newStartTime).toMinutes()
        })
    }

    @Test
    fun impossibleCompressionReportsMaxReduction() {
        // Zwei 10er-Abstände, Untergrenze 5 → maximal 10 Minuten aufholbar, 12 gefordert.
        val slots = listOf(slot(0), slot(10), slot(20))
        val result = EventScheduleLogic.computeShift(slots, deltaMinutes = 12, targetSlotId = slots[2].id)
        val impossible = assertIs<ShiftResult.CompressionImpossible>(result)
        assertEquals(10L, impossible.maxReductionMinutes)
    }
}
