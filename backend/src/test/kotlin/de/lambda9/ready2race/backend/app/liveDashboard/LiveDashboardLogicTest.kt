package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardInvoiceState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardRequirementStatusDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardScope
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckStatus
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveDashboardLogicTest {

    private val start = LocalDateTime.of(2026, 7, 29, 14, 0)

    // --- teamOnWaterAt ---

    @Test
    fun onWaterWhenWholeCrewCheckedOut() {
        val scans = listOf(
            "EXIT" to start.minusMinutes(10),
            "EXIT" to start.minusMinutes(8),
            "EXIT" to start.minusMinutes(12),
        )
        assertEquals(start.minusMinutes(8), LiveDashboardLogic.teamOnWaterAt(scans))
    }

    @Test
    fun notOnWaterWhenAnyCrewMemberMissingOrCheckedIn() {
        // Eine Person nie gescannt
        assertNull(
            LiveDashboardLogic.teamOnWaterAt(listOf("EXIT" to start, null))
        )
        // Eine Person wieder eingecheckt (letzter Scan ENTRY)
        assertNull(
            LiveDashboardLogic.teamOnWaterAt(listOf("EXIT" to start, "ENTRY" to start.plusMinutes(1)))
        )
    }

    @Test
    fun notOnWaterWithoutKnownCrew() {
        assertNull(LiveDashboardLogic.teamOnWaterAt(emptyList()))
    }

    // --- computeTimeCheck ---

    @Test
    fun noWindowConfiguredYieldsNoTimeCheck() {
        assertNull(LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(30), null, null))
    }

    @Test
    fun noStartTimeYieldsNoTimeCheck() {
        assertNull(LiveDashboardLogic.computeTimeCheck(null, start.minusMinutes(30), 120, 15))
    }

    @Test
    fun missingCheckYieldsNotChecked() {
        val result = LiveDashboardLogic.computeTimeCheck(start, null, 120, 15)!!
        assertEquals(TimeCheckStatus.NOT_CHECKED, result.status)
        assertNull(result.deltaMinutes)
    }

    @Test
    fun checkWithinWindowIsOk() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(60), 120, 15)!!
        assertEquals(TimeCheckStatus.OK, result.status)
        assertEquals(60L, result.deltaMinutes)
    }

    @Test
    fun boundariesAreInclusive() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(120), 120, 15)!!.status)
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(15), 120, 15)!!.status)
    }

    @Test
    fun checkTooFarBeforeStartIsTooEarly() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(121), 120, 15)!!
        assertEquals(TimeCheckStatus.TOO_EARLY, result.status)
        assertEquals(121L, result.deltaMinutes)
    }

    @Test
    fun checkTooCloseToStartIsLate() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(14), 120, 15)!!
        assertEquals(TimeCheckStatus.LATE, result.status)
    }

    @Test
    fun checkAfterStartIsLateWhenLatestConfigured() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.plusMinutes(5), 120, 15)!!
        assertEquals(TimeCheckStatus.LATE, result.status)
        assertEquals(-5L, result.deltaMinutes)
    }

    @Test
    fun oneSidedEarliestOnlyWindow() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(5), 120, null)!!.status)
        assertEquals(TimeCheckStatus.TOO_EARLY, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(180), 120, null)!!.status)
    }

    @Test
    fun oneSidedLatestOnlyWindow() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(180), null, 15)!!.status)
        assertEquals(TimeCheckStatus.LATE, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(5), null, 15)!!.status)
    }

    // --- deriveInvoiceState ---

    @Test
    fun noInvoicesIsNone() {
        assertEquals(LiveDashboardInvoiceState.NONE, LiveDashboardLogic.deriveInvoiceState(emptyList()))
    }

    @Test
    fun anyUnpaidInvoiceIsOpen() {
        assertEquals(
            LiveDashboardInvoiceState.OPEN,
            LiveDashboardLogic.deriveInvoiceState(listOf(LocalDateTime.now(), null))
        )
    }

    @Test
    fun allPaidIsPaid() {
        assertEquals(
            LiveDashboardInvoiceState.PAID,
            LiveDashboardLogic.deriveInvoiceState(listOf(LocalDateTime.now(), LocalDateTime.now()))
        )
    }

    // --- deriveMatchState ---

    @Test
    fun currentlyRunningWinsOverEverything() {
        assertEquals(
            LiveDashboardMatchState.RUNNING,
            LiveDashboardLogic.deriveMatchState(true, null, null, listOf(true, true))
        )
    }

    @Test
    fun allPlacesSetButNobodyFinishedAwaitsFinish() {
        // Testkatalog D15: vollständige Ergebnisse sind KEIN Beenden. Bis zum 06.08.2026 stand
        // hier FINISHED - der Lauf verschwand damit aus dem Live-Tab und bot "Lauf aktivieren"
        // statt "Lauf beenden" an.
        assertEquals(
            LiveDashboardMatchState.AWAITING_FINISH,
            LiveDashboardLogic.deriveMatchState(false, start, null, listOf(true, true))
        )
    }

    @Test
    fun activeMatchWithCompleteResultsStaysRunning() {
        // RUNNING steht vor AWAITING_FINISH: ein aktiver Lauf hat den Beenden-Knopf ohnehin.
        assertEquals(
            LiveDashboardMatchState.RUNNING,
            LiveDashboardLogic.deriveMatchState(true, start, null, listOf(true, true))
        )
    }

    @Test
    fun finishedStaysFinishedEvenWithCompleteResults() {
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(false, start, start.plusMinutes(9), listOf(true, true))
        )
    }

    @Test
    fun noTeamsIsNeverFinished() {
        assertEquals(
            LiveDashboardMatchState.UPCOMING,
            LiveDashboardLogic.deriveMatchState(false, start, null, emptyList())
        )
    }

    @Test
    fun missingStartTimeIsUnscheduled() {
        assertEquals(
            LiveDashboardMatchState.UNSCHEDULED,
            LiveDashboardLogic.deriveMatchState(false, null, null, listOf(false, false))
        )
    }

    @Test
    fun startTimeInPastWithoutPlacesIsStillUpcoming() {
        assertEquals(
            LiveDashboardMatchState.UPCOMING,
            LiveDashboardLogic.deriveMatchState(false, LocalDateTime.now().minusHours(1), null, listOf(true, false))
        )
    }

    @Test
    fun failedTeamWithoutPlaceCountsAsResult() {
        assertTrue(LiveDashboardLogic.teamHasResult(1, false, false))
        assertTrue(LiveDashboardLogic.teamHasResult(null, true, false))
        assertFalse(LiveDashboardLogic.teamHasResult(null, false, false))
        assertEquals(
            LiveDashboardMatchState.AWAITING_FINISH,
            LiveDashboardLogic.deriveMatchState(
                false,
                start,
                null,
                listOf(
                    LiveDashboardLogic.teamHasResult(1, false, false),
                    LiveDashboardLogic.teamHasResult(null, true, false),
                ),
            )
        )
    }

    @Test
    fun deregisteredTeamNeedsNoResult() {
        assertTrue(LiveDashboardLogic.teamHasResult(null, false, true))
    }

    @Test
    fun matchWithDeregisteredTeamCanFinish() {
        assertEquals(
            LiveDashboardMatchState.AWAITING_FINISH,
            LiveDashboardLogic.deriveMatchState(
                false,
                start,
                null,
                listOf(
                    LiveDashboardLogic.teamHasResult(1, false, false),
                    LiveDashboardLogic.teamHasResult(null, false, true),
                ),
            )
        )
    }

    @Test
    fun finishedAtBeatsIncompleteResults() {
        // Ohne Ergebnisse beendet: bisher fiel das auf UPCOMING zurück (A4-Loch).
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(false, start, start.plusMinutes(9), listOf(false, false)),
        )
    }

    @Test
    fun cancelledMatchWithCompleteResultsStaysSkipped() {
        // SKIPPED steht vor AWAITING_FINISH: einen abgesagten Lauf muss niemand mehr beenden.
        assertEquals(
            LiveDashboardMatchState.SKIPPED,
            LiveDashboardLogic.deriveMatchState(false, start, null, listOf(true, true), skipped = true),
        )
    }

    @Test
    fun cancelledSlotMarksItsMatchAsSkipped() {
        // Befund A fürs Schiedsrichter-Dashboard: kennzeichnen statt verstecken - der
        // Schiedsrichter muss die Absage sehen, um sie im Zeitplan zurücknehmen zu können.
        assertEquals(
            LiveDashboardMatchState.SKIPPED,
            LiveDashboardLogic.deriveMatchState(false, start, null, listOf(false, false), skipped = true),
        )
    }

    @Test
    fun cancelledButActiveMatchStillShowsRunning() {
        // Wirklichkeit schlägt Plan: Der Zustand entsteht seit der Schutzregel in
        // EventScheduleService.setSlotSkipped nicht mehr neu, Altdaten können ihn aber tragen -
        // und dann darf das Dashboard nicht behaupten, es passiere gerade nichts.
        assertEquals(
            LiveDashboardMatchState.RUNNING,
            LiveDashboardLogic.deriveMatchState(true, start, null, listOf(false, false), skipped = true),
        )
    }

    @Test
    fun cancelledMatchWithResultsStaysFinished() {
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(false, start, start.plusMinutes(9), listOf(true, true), skipped = true),
        )
    }

    // --- selectForScope ---

    private fun match(state: LiveDashboardMatchState, name: String) = LiveDashboardMatchDto(
        matchId = UUID.randomUUID(),
        state = state,
        competitionId = UUID.randomUUID(),
        competitionName = "Coastal",
        categoryName = null,
        roundName = null,
        matchName = name,
        executionOrder = 0,
        startTime = start,
        startedAt = null,
        currentlyRunning = state == LiveDashboardMatchState.RUNNING,
        elapsedMinutes = null,
        teams = emptyList(),
        raceClockerPolledAt = null,
        raceClockerPollError = null,
        raceClockerAutoPausedAt = null,
    )

    @Test
    fun liveScopeKeepsEveryRunningMatch() {
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.RUNNING, "Vorlauf 2"),
            match(LiveDashboardMatchState.RUNNING, "Vorlauf 3"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Vorlauf 2", "Vorlauf 3"), selected.map { it.matchName })
    }

    @Test
    fun liveScopeKeepsMatchesWaitingToBeFinished() {
        // Der Kern der D15-Korrektur: ohne diesen Zweig bliebe der Lauf, auf dessen Beenden alles
        // wartet, aus dem Live-Tab verschwunden.
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.AWAITING_FINISH, "Vorlauf 2"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Vorlauf 2"), selected.map { it.matchName })
    }

    @Test
    fun liveScopeKeepsRunningAndAwaitingSideBySide() {
        val matches = listOf(
            match(LiveDashboardMatchState.AWAITING_FINISH, "Vorlauf 1"),
            match(LiveDashboardMatchState.RUNNING, "Vorlauf 2"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Vorlauf 1", "Vorlauf 2"), selected.map { it.matchName })
    }

    @Test
    fun liveScopeFallsBackToTheNextUpcomingMatch() {
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.UPCOMING, "Vorlauf 2"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Vorlauf 2"), selected.map { it.matchName })
    }

    @Test
    fun liveScopeSkipsCancelledMatchesWhenPickingTheNextOne() {
        // Der Ausschnitt "was ist jetzt dran" darf nicht auf einem abgesagten Lauf stehen bleiben;
        // in der Gesamtliste (ALL) bleibt er als gekennzeichneter Eintrag sichtbar.
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.SKIPPED, "Vorlauf 2"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Finale"), selected.map { it.matchName })
        assertEquals(3, LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.ALL).size)
    }

    @Test
    fun liveScopeIsEmptyWhenNothingIsRunningOrUpcoming() {
        val matches = listOf(match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"))

        assertTrue(LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE).isEmpty())
    }

    @Test
    fun allScopeKeepsEverything() {
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.RUNNING, "Vorlauf 2"),
            match(LiveDashboardMatchState.UNSCHEDULED, "Finale"),
        )

        assertEquals(3, LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.ALL).size)
    }

    // --- summarizeRequirements ---

    private fun requirement(
        optional: Boolean = false,
        checked: Boolean = true,
        timeStatus: TimeCheckStatus? = null,
    ) = LiveDashboardRequirementStatusDto(
        requirementId = UUID.randomUUID(),
        name = "Bedingung",
        description = null,
        optional = optional,
        checked = checked,
        checkedAt = if (checked) start.minusMinutes(30) else null,
        note = null,
        timeCheck = timeStatus?.let { TimeCheckDto(30, it) },
    )

    @Test
    fun summaryCountsFulfilledAndMissing() {
        val summary = LiveDashboardLogic.summarizeRequirements(
            listOf(
                requirement(),
                requirement(checked = false),
                requirement(optional = true, checked = false),
            )
        )
        assertEquals(3, summary.total)
        assertEquals(1, summary.fulfilled)
        assertEquals(1, summary.missingRequired)
        assertEquals(1, summary.missingOptional)
        assertEquals(0, summary.timeIssues)
    }

    @Test
    fun summaryCountsChecksOutsideTheWindow() {
        val summary = LiveDashboardLogic.summarizeRequirements(
            listOf(
                requirement(timeStatus = TimeCheckStatus.OK),
                requirement(timeStatus = TimeCheckStatus.LATE),
                requirement(timeStatus = TimeCheckStatus.TOO_EARLY),
            )
        )
        assertEquals(3, summary.fulfilled)
        assertEquals(2, summary.timeIssues)
    }

    @Test
    fun summaryOfNothingIsEmpty() {
        val summary = LiveDashboardLogic.summarizeRequirements(emptyList())
        assertEquals(0, summary.total)
        assertEquals(0, summary.fulfilled)
        assertEquals(0, summary.missingRequired)
        assertEquals(0, summary.missingOptional)
        assertEquals(0, summary.timeIssues)
    }

    // --- requirementApplies ---

    @Test
    fun globalAssignmentAppliesToEveryone() {
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(null), UUID.randomUUID()))
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(null), null))
    }

    @Test
    fun namedAssignmentAppliesOnlyToMatchingRole() {
        val roleId = UUID.randomUUID()
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(roleId), roleId))
        assertFalse(LiveDashboardLogic.requirementApplies(listOf(roleId), UUID.randomUUID()))
        assertFalse(LiveDashboardLogic.requirementApplies(listOf(roleId), null))
    }
}
