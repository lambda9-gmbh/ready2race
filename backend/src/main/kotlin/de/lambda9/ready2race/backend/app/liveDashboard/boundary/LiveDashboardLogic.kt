package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardInvoiceState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckStatus
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

object LiveDashboardLogic {

    fun computeTimeCheck(
        startTime: LocalDateTime?,
        checkedAt: LocalDateTime?,
        earliestMinutesBefore: Int?,
        latestMinutesBefore: Int?,
    ): TimeCheckDto? {
        if (earliestMinutesBefore == null && latestMinutesBefore == null) return null
        if (startTime == null) return null
        if (checkedAt == null) return TimeCheckDto(null, TimeCheckStatus.NOT_CHECKED)

        val deltaMinutes = Duration.between(checkedAt, startTime).toMinutes()
        val status = when {
            earliestMinutesBefore != null && deltaMinutes > earliestMinutesBefore -> TimeCheckStatus.TOO_EARLY
            latestMinutesBefore != null && deltaMinutes < latestMinutesBefore -> TimeCheckStatus.LATE
            else -> TimeCheckStatus.OK
        }
        return TimeCheckDto(deltaMinutes, status)
    }

    fun deriveInvoiceState(paidAts: List<LocalDateTime?>): LiveDashboardInvoiceState = when {
        paidAts.isEmpty() -> LiveDashboardInvoiceState.NONE
        paidAts.any { it == null } -> LiveDashboardInvoiceState.OPEN
        else -> LiveDashboardInvoiceState.PAID
    }

    fun deriveMatchState(
        currentlyRunning: Boolean,
        startTime: LocalDateTime?,
        teamResults: List<Boolean>,
    ): LiveDashboardMatchState = when {
        currentlyRunning -> LiveDashboardMatchState.RUNNING
        teamResults.isNotEmpty() && teamResults.all { it } -> LiveDashboardMatchState.FINISHED
        startTime == null -> LiveDashboardMatchState.UNSCHEDULED
        else -> LiveDashboardMatchState.UPCOMING
    }

    fun requirementApplies(
        assignedNamedParticipants: List<UUID?>,
        participantNamedParticipantId: UUID?,
    ): Boolean = assignedNamedParticipants.any { it == null } ||
        (participantNamedParticipantId != null && assignedNamedParticipants.contains(participantNamedParticipantId))
}
