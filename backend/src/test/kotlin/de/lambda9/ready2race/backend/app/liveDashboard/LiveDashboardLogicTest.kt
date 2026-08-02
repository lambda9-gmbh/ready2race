package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardInvoiceState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchState
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
            LiveDashboardLogic.deriveMatchState(true, null, listOf(true, true))
        )
    }

    @Test
    fun allPlacesSetIsFinished() {
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(false, start, listOf(true, true))
        )
    }

    @Test
    fun noTeamsIsNeverFinished() {
        assertEquals(
            LiveDashboardMatchState.UPCOMING,
            LiveDashboardLogic.deriveMatchState(false, start, emptyList())
        )
    }

    @Test
    fun missingStartTimeIsUnscheduled() {
        assertEquals(
            LiveDashboardMatchState.UNSCHEDULED,
            LiveDashboardLogic.deriveMatchState(false, null, listOf(false, false))
        )
    }

    @Test
    fun startTimeInPastWithoutPlacesIsStillUpcoming() {
        assertEquals(
            LiveDashboardMatchState.UPCOMING,
            LiveDashboardLogic.deriveMatchState(false, LocalDateTime.now().minusHours(1), listOf(true, false))
        )
    }

    @Test
    fun failedTeamWithoutPlaceCountsAsResult() {
        assertTrue(LiveDashboardLogic.teamHasResult(1, false, false))
        assertTrue(LiveDashboardLogic.teamHasResult(null, true, false))
        assertFalse(LiveDashboardLogic.teamHasResult(null, false, false))
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(
                false,
                start,
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
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(
                false,
                start,
                listOf(
                    LiveDashboardLogic.teamHasResult(1, false, false),
                    LiveDashboardLogic.teamHasResult(null, false, true),
                ),
            )
        )
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
