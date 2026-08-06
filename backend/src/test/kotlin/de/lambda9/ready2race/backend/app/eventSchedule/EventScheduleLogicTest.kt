package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleLogic
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ShiftResult
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ShiftSlot
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleError
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import de.lambda9.ready2race.backend.app.eventSchedule.entity.ShiftTargetProblem
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import de.lambda9.ready2race.backend.xls.XLSReadError
import org.apache.poi.ss.usermodel.CellType
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // --- freeSlotOrNull ---

    @Test
    fun freeSlotYieldsFreeSlotInfo() {
        val slotId = UUID.randomUUID()
        val startTime = slotNow.plusMinutes(30)

        val result = EventScheduleLogic.freeSlotOrNull(
            slotId = slotId,
            isFree = true,
            name = "Mittagspause",
            startTime = startTime,
            skipped = false,
        )

        val info = assertNotNull(result)
        assertEquals(slotId, info.slotId)
        assertEquals(startTime, info.startTime)
        assertEquals("Mittagspause", info.name)
    }

    @Test
    fun nonFreeSlotYieldsNoFreeSlotInfo() {
        assertNull(
            EventScheduleLogic.freeSlotOrNull(
                slotId = UUID.randomUUID(),
                isFree = false,
                name = null,
                startTime = slotNow,
                skipped = false,
            ),
        )
    }

    @Test
    fun skippedFreeSlotYieldsNoFreeSlotInfo() {
        assertNull(
            EventScheduleLogic.freeSlotOrNull(
                slotId = UUID.randomUUID(),
                isFree = true,
                name = "Mittagspause",
                startTime = slotNow,
                skipped = true,
            ),
        )
    }

    // --- skippedMatchIdOrNull ---
    //
    // Die andere Seite derselben Zeile: pendingSlotOrNull löst nur den Fall "Runde noch nicht
    // gesetzt" auf. Sobald die Runde gesetzt ist, gibt es einen echten Lauf, und der muss bei einer
    // Absage aus "nächste Läufe" von Athleten-Anzeige und Kiosk herausfallen.

    private fun skippedMatchArgs(
        setupMatchId: UUID? = UUID.randomUUID(),
        skipped: Boolean = true,
        roundMaterialized: Boolean = true,
        matchExists: Boolean = true,
    ) = EventScheduleLogic.skippedMatchIdOrNull(
        setupMatchId = setupMatchId,
        skipped = skipped,
        roundMaterialized = roundMaterialized,
        matchExists = matchExists,
    )

    @Test
    fun skippedSlotWithMaterializedRoundYieldsItsMatchId() {
        val setupMatchId = UUID.randomUUID()

        assertEquals(setupMatchId, skippedMatchArgs(setupMatchId = setupMatchId))
    }

    @Test
    fun notSkippedSlotYieldsNoMatchIdToHide() {
        // Der Regelfall: ein gesetzter, nicht abgesagter Lauf bleibt in "nächste Läufe" stehen.
        assertNull(skippedMatchArgs(skipped = false))
    }

    @Test
    fun skippedSlotWithoutMatchYieldsNoMatchIdToHide() {
        // Runde noch nicht gesetzt: Es gibt keinen echten Lauf zu verbergen, der Slot liefert
        // stattdessen schon über pendingSlotOrNull keinen Platzhalter mehr.
        assertNull(skippedMatchArgs(roundMaterialized = false, matchExists = false))
    }

    @Test
    fun freeSlotYieldsNoMatchIdToHide() {
        assertNull(skippedMatchArgs(setupMatchId = null))
    }

    @Test
    fun onlySkippedSlotsWithMatchAmongMixedStatesYieldMatchIds() {
        val skippedLinked = UUID.randomUUID()
        val plainLinked = UUID.randomUUID()
        val skippedWaiting = UUID.randomUUID()

        val hidden = listOfNotNull(
            skippedMatchArgs(setupMatchId = skippedLinked),
            skippedMatchArgs(setupMatchId = plainLinked, skipped = false),
            skippedMatchArgs(setupMatchId = skippedWaiting, roundMaterialized = false, matchExists = false),
            skippedMatchArgs(setupMatchId = null),
        )

        assertEquals(listOf(skippedLinked), hidden)
    }

    // --- matchUnderway ---

    @Test
    fun matchWithoutStartIsNotUnderway() {
        assertFalse(EventScheduleLogic.matchUnderway(startedAt = null, currentlyRunning = false))
    }

    @Test
    fun activatedMatchWithoutRecordedStartIsUnderway() {
        // Befund B: das Fenster zwischen "Boote gehen an den Start" (Schiedsrichter aktiviert) und
        // "die Zeitnahme meldet den Start". Nur auf started_at zu schauen, ließ hier eine Absage zu -
        // der Lauf war danach abgesagt UND laufend zugleich.
        assertTrue(EventScheduleLogic.matchUnderway(startedAt = null, currentlyRunning = true))
    }

    @Test
    fun recordedStartIsUnderwayEvenWhenNoLongerActive() {
        // Ein beendeter Lauf trägt started_at, aber currently_running = false - absagen lässt er
        // sich trotzdem nicht mehr.
        assertTrue(EventScheduleLogic.matchUnderway(startedAt = slotNow, currentlyRunning = false))
    }

    @Test
    fun runningMatchWithRecordedStartIsUnderway() {
        assertTrue(EventScheduleLogic.matchUnderway(startedAt = slotNow, currentlyRunning = true))
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

    // --- overtakesPredecessor ---

    private fun entry(minutesAfterBase: Long) =
        de.lambda9.ready2race.backend.app.eventSchedule.boundary.ShiftPreviewEntry(
            UUID.randomUUID(),
            base.plusMinutes(minutesAfterBase),
            base.plusMinutes(minutesAfterBase),
        )

    @Test
    fun noPredecessorMeansNoLimit() {
        assertEquals(false, EventScheduleLogic.overtakesPredecessor(listOf(entry(-100)), null))
    }

    @Test
    fun newTimeBeforeThePredecessorOvertakesIt() {
        val predecessor = base.minusMinutes(5)
        val entries = listOf(entry(0).copy(newStartTime = base.minusMinutes(10)))
        assertEquals(true, EventScheduleLogic.overtakesPredecessor(entries, predecessor))
    }

    @Test
    fun newTimeAtOrAfterThePredecessorIsFine() {
        val predecessor = base.minusMinutes(5)
        val atPredecessor = listOf(entry(0).copy(newStartTime = predecessor))
        val afterPredecessor = listOf(entry(0).copy(newStartTime = predecessor.plusMinutes(1)))
        assertEquals(false, EventScheduleLogic.overtakesPredecessor(atPredecessor, predecessor))
        assertEquals(false, EventScheduleLogic.overtakesPredecessor(afterPredecessor, predecessor))
    }

    @Test
    fun oneOffendingEntryAmongManyIsEnoughToBlock() {
        val predecessor = base.minusMinutes(5)
        val entries = listOf(
            entry(0).copy(newStartTime = predecessor.plusMinutes(10)),
            entry(10).copy(newStartTime = predecessor.minusMinutes(1)),
        )
        assertEquals(true, EventScheduleLogic.overtakesPredecessor(entries, predecessor))
    }

    // --- Ablehnungsgründe des Verschiebe-Dialogs (B4/B21) ---
    // Früher liefen alle vier in dieselbe Meldung "Shift request parameters are inconsistent".

    @Test
    fun compressionNeedsAPositiveDelay() {
        val slots = listOf(slot(0), slot(10))
        assertEquals(
            ShiftTargetProblem.NEGATIVE_DELAY,
            EventScheduleLogic.shiftTargetProblem(slots, slots[1].id, deltaMinutes = -10),
        )
        assertEquals(
            ShiftTargetProblem.NEGATIVE_DELAY,
            EventScheduleLogic.shiftTargetProblem(slots, slots[1].id, deltaMinutes = 0),
        )
    }

    @Test
    fun compressionTargetMustLieBehindTheStartSlot() {
        val slots = listOf(slot(0), slot(10))
        // Der Start-Slot selbst ist kein gültiges Ziel ...
        assertEquals(
            ShiftTargetProblem.TARGET_NOT_AFTER_START,
            EventScheduleLogic.shiftTargetProblem(slots, slots[0].id, deltaMinutes = 5),
        )
        // ... ebensowenig ein Slot, der zu diesem Renntag gar nicht gehört.
        assertEquals(
            ShiftTargetProblem.TARGET_NOT_AFTER_START,
            EventScheduleLogic.shiftTargetProblem(slots, UUID.randomUUID(), deltaMinutes = 5),
        )
        assertEquals(
            ShiftTargetProblem.TARGET_NOT_AFTER_START,
            EventScheduleLogic.shiftTargetProblem(slots, null, deltaMinutes = 5),
        )
    }

    @Test
    fun validCompressionRequestHasNoProblem() {
        val slots = listOf(slot(0), slot(10))
        assertNull(EventScheduleLogic.shiftTargetProblem(slots, slots[1].id, deltaMinutes = 5))
    }

    @Test
    fun aShiftWithinTheDayLeavesNoEntryBehind() {
        val entries = listOf(entry(0), entry(60))
        assertNull(EventScheduleLogic.firstEntryLeavingDay(entries, base.toLocalDate()))
    }

    @Test
    fun theEarliestEntryPastMidnightIsReported() {
        // base ist 10:00; +14 h landet um 00:00 des Folgetags, +15 h um 01:00.
        val late = entry(0).copy(newStartTime = base.plusHours(15))
        val first = entry(60).copy(newStartTime = base.plusHours(14))
        val leaving = EventScheduleLogic.firstEntryLeavingDay(listOf(late, first), base.toLocalDate())
        assertNotNull(leaving)
        assertEquals(first.slotId, leaving.slotId)
        assertEquals(base.plusHours(14), leaving.newStartTime)
    }

    @Test
    fun maxAdvanceIsTheSmallestDistanceToThePredecessor() {
        val predecessor = base.minusMinutes(20)
        // Alte Zeiten 20 bzw. 50 Minuten hinter dem Vorgänger → höchstens 20 Minuten vorziehbar.
        val entries = listOf(entry(0), entry(30))
        assertEquals(20L, EventScheduleLogic.maxAdvanceMinutes(entries, predecessor))
    }

    @Test
    fun maxAdvanceIsZeroWhenASlotAlreadySitsOnThePredecessor() {
        val predecessor = base
        assertEquals(0L, EventScheduleLogic.maxAdvanceMinutes(listOf(entry(0), entry(30)), predecessor))
        assertEquals(0L, EventScheduleLogic.maxAdvanceMinutes(listOf(entry(-5)), predecessor))
    }

    // --- XLS-Lesefehler → Import-Meldung ---
    // Vorher warf `mapError { ImportFileUnreadable }` Zeile, Spalte und Wert weg.

    @Test
    fun excelRowNumbersAreOneAheadOfPoi() {
        // Kopfzeile ist POI-Zeile 0 / Excel-Zeile 1, die erste Datenzeile POI 1 / Excel 2.
        assertEquals(1, EventScheduleLogic.excelRowNumber(0))
        assertEquals(2, EventScheduleLogic.excelRowNumber(1))
    }

    @Test
    fun unparsableCellKeepsRowColumnAndValue() {
        val error = EventScheduleLogic.importErrorFor(
            XLSReadError.CellError.ParseError.UnparsableStringValue(2, "Uhrzeit", "viertel nach zehn")
        )
        val unparsable = assertIs<EventScheduleError.ImportCellUnparsable>(error)
        // POI-Zeile 2 ist die dritte Zeile des Blatts - genau die Zeile 3, die Excel anzeigt.
        assertEquals(3, unparsable.row)
        assertEquals("Uhrzeit", unparsable.column)
        assertEquals("viertel nach zehn", unparsable.value)
    }

    @Test
    fun blankCellKeepsRowAndColumn() {
        val error = EventScheduleLogic.importErrorFor(
            XLSReadError.CellError.ParseError.CellBlank(4, "Lauf")
        )
        val blank = assertIs<EventScheduleError.ImportCellBlank>(error)
        assertEquals(5, blank.row)
        assertEquals("Lauf", blank.column)
    }

    @Test
    fun wrongCellTypeKeepsBothTypes() {
        val error = EventScheduleLogic.importErrorFor(
            XLSReadError.CellError.ParseError.WrongCellType(1, "Dauer", CellType.STRING, CellType.NUMERIC)
        )
        val wrongType = assertIs<EventScheduleError.ImportWrongCellType>(error)
        assertEquals(2, wrongType.row)
        assertEquals("Dauer", wrongType.column)
        assertEquals("STRING", wrongType.actual)
        assertEquals("NUMERIC", wrongType.expected)
    }

    @Test
    fun missingColumnKeepsTheExpectedHeader() {
        val error = EventScheduleLogic.importErrorFor(XLSReadError.CellError.ColumnUnknown("Datum"))
        assertEquals("Datum", assertIs<EventScheduleError.ImportColumnMissing>(error).column)
    }

    @Test
    fun fileLevelErrorsStayFileLevelErrors() {
        assertIs<EventScheduleError.ImportFileUnreadable>(
            EventScheduleLogic.importErrorFor(XLSReadError.FileError)
        )
        assertIs<EventScheduleError.ImportNoHeaders>(
            EventScheduleLogic.importErrorFor(XLSReadError.NoHeaders)
        )
    }

    // --- Die Meldungen selbst: eigener Text, eigener ErrorCode, strukturierte Werte ---

    @Test
    fun theFourShiftRejectionsHaveDistinctCodesAndTexts() {
        val responses = listOf(
            EventScheduleError.ShiftWithoutChange,
            EventScheduleError.ShiftTargetInvalid(ShiftTargetProblem.TARGET_NOT_AFTER_START),
            EventScheduleError.ShiftTargetInvalid(ShiftTargetProblem.NEGATIVE_DELAY),
            EventScheduleError.ShiftLeavesRaceDay(UUID.randomUUID(), base.plusHours(15), base.toLocalDate()),
            EventScheduleError.ShiftOvertakesPredecessor(base.minusMinutes(20), 20),
        ).map { it.respond() }

        assertEquals(responses.size, responses.map { it.message }.toSet().size)
        assertTrue(responses.all { it.errorCode != null })
        assertEquals(
            setOf(
                ErrorCode.SCHEDULE_SHIFT_WITHOUT_CHANGE,
                ErrorCode.SCHEDULE_SHIFT_TARGET_INVALID,
                ErrorCode.SCHEDULE_SHIFT_LEAVES_RACE_DAY,
                ErrorCode.SCHEDULE_SHIFT_OVERTAKES_PREDECESSOR,
            ),
            responses.mapNotNull { it.errorCode }.toSet(),
        )
    }

    @Test
    fun rejectionsCarryTheValuesTheUserNeedsToCorrectThem() {
        val slotId = UUID.randomUUID()
        val leaves = EventScheduleError.ShiftLeavesRaceDay(slotId, base.plusHours(15), base.toLocalDate()).respond()
        assertEquals(slotId.toString(), leaves.details?.get("slotId"))
        assertEquals(base.plusHours(15).toString(), leaves.details?.get("newStartTime"))

        val overtakes = EventScheduleError.ShiftOvertakesPredecessor(base.minusMinutes(20), 20).respond()
        assertEquals(base.minusMinutes(20).toString(), overtakes.details?.get("earliestStartTime"))
        assertEquals(20L, overtakes.details?.get("maxAdvanceMinutes"))

        // Die beiden Ziel-Probleme sind auch maschinenlesbar unterscheidbar, nicht nur im Text.
        assertEquals(
            "NEGATIVE_DELAY",
            EventScheduleError.ShiftTargetInvalid(ShiftTargetProblem.NEGATIVE_DELAY).respond().details?.get("problem"),
        )
    }

    @Test
    fun importMessagesNameRowColumnAndValue() {
        val error = EventScheduleError.ImportCellUnparsable(3, "Uhrzeit", "viertel nach zehn").respond()
        assertEquals(ErrorCode.SPREADSHEET_UNPARSABLE_STRING, error.errorCode)
        assertEquals(3, error.details?.get("row"))
        assertEquals("Uhrzeit", error.details?.get("column"))
        assertEquals("viertel nach zehn", error.details?.get("value"))
        assertTrue(error.message.contains("row 3"))
    }
}
