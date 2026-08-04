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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    // --- pendingSlotOrNull ---
    //
    // Übernommen aus AthleteBoardLogicTest, seit die "nur WAITING zählt"-Regel hierher gezogen
    // wurde, damit Athleten-Anzeige und Live-Dashboard sie nicht mehr getrennt pflegen.

    private val slotNow: LocalDateTime = LocalDateTime.of(2026, 8, 2, 10, 0)

    private fun waitingArgs(
        slotId: UUID = UUID.randomUUID(),
        setupMatchId: UUID? = UUID.randomUUID(),
        startTime: LocalDateTime = slotNow.plusMinutes(30),
        competitionId: UUID? = UUID.randomUUID(),
        competitionName: String? = "Kanu",
        roundName: String? = "Vorlauf",
        matchName: String? = "Lauf 1",
        skipped: Boolean = false,
        roundMaterialized: Boolean = false,
        matchExists: Boolean = false,
    ) = EventScheduleLogic.pendingSlotOrNull(
        slotId = slotId,
        setupMatchId = setupMatchId,
        startTime = startTime,
        competitionId = competitionId,
        competitionName = competitionName,
        roundName = roundName,
        matchName = matchName,
        skipped = skipped,
        roundMaterialized = roundMaterialized,
        matchExists = matchExists,
    )

    @Test
    fun waitingSlotYieldsPendingSlotInfo() {
        val slotId = UUID.randomUUID()
        val setupMatchId = UUID.randomUUID()
        val competitionId = UUID.randomUUID()
        val startTime = slotNow.plusMinutes(30)

        val result = waitingArgs(
            slotId = slotId,
            setupMatchId = setupMatchId,
            startTime = startTime,
            competitionId = competitionId,
            competitionName = "Kanu",
            roundName = "Vorlauf",
            matchName = "Lauf 1",
        )

        val info = assertNotNull(result)
        assertEquals(slotId, info.slotId)
        assertEquals(setupMatchId, info.setupMatchId)
        assertEquals(startTime, info.startTime)
        assertEquals(competitionId, info.competitionId)
        assertEquals("Kanu", info.competitionName)
        assertEquals("Vorlauf", info.roundName)
        assertEquals("Lauf 1", info.matchName)
    }

    @Test
    fun freeSlotWithoutSetupMatchYieldsNoPendingSlot() {
        assertNull(waitingArgs(setupMatchId = null))
    }

    @Test
    fun slotWithoutCompetitionYieldsNoPendingSlot() {
        // Verteidigt gegen einen kaputten Join: ohne Kompetition kein Ziel für einen Platzhalter.
        assertNull(waitingArgs(competitionId = null))
    }

    @Test
    fun skippedSlotYieldsNoPendingSlot() {
        assertNull(waitingArgs(skipped = true))
    }

    @Test
    fun linkedSlotYieldsNoPendingSlot() {
        assertNull(waitingArgs(roundMaterialized = true, matchExists = true))
    }

    @Test
    fun obsoleteSlotYieldsNoPendingSlot() {
        assertNull(waitingArgs(roundMaterialized = true, matchExists = false))
    }

    @Test
    fun onlyWaitingSlotsAmongMixedStatesYieldPendingSlots() {
        val waiting = waitingArgs(matchName = "wartend")
        val skipped = waitingArgs(matchName = "übersprungen", skipped = true)
        val linked = waitingArgs(matchName = "verlinkt", roundMaterialized = true, matchExists = true)
        val obsolete = waitingArgs(matchName = "entfallen", roundMaterialized = true, matchExists = false)
        val free = waitingArgs(matchName = "frei", setupMatchId = null)

        val results = listOf(waiting, skipped, linked, obsolete, free)

        assertEquals(listOf("wartend"), results.mapNotNull { it?.matchName })
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
