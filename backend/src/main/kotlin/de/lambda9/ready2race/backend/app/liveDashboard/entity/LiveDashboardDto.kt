package de.lambda9.ready2race.backend.app.liveDashboard.entity

import java.time.LocalDateTime
import java.util.UUID

enum class LiveDashboardMatchState { RUNNING, FINISHED, UPCOMING, UNSCHEDULED }

enum class LiveDashboardInvoiceState { PAID, OPEN, NONE }

enum class TimeCheckStatus { OK, TOO_EARLY, LATE, NOT_CHECKED }

data class TimeCheckDto(
    val deltaMinutes: Long?,
    val status: TimeCheckStatus,
)

data class LiveDashboardRequirementStatusDto(
    val requirementId: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val checked: Boolean,
    val checkedAt: LocalDateTime?,
    val note: String?,
    val timeCheck: TimeCheckDto?,
)

data class LiveDashboardParticipantDto(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val namedRole: String?,
    val year: Int?,
    val gender: String?,
    val externalClubName: String?,
    /** Name of the participant this one replaced, if they were substituted into this round. */
    val substitutedFor: String?,
    val substitutionReason: String?,
    val requirements: List<LiveDashboardRequirementStatusDto>,
)

data class LiveDashboardTeamDto(
    val teamId: UUID,
    val teamName: String?,
    val clubName: String?,
    val actualClubName: String?,
    val startNumber: Int?,
    val place: Int?,
    val time: String?,
    val failed: Boolean,
    val failedReason: String?,
    /** Zeitstrafe in Sekunden; die Ergebniszeit enthält sie bereits. */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val deregistered: Boolean,
    val deregisteredReason: String?,
    val invoiceState: LiveDashboardInvoiceState,
    val participants: List<LiveDashboardParticipantDto>,
)

data class LiveDashboardMatchDto(
    val matchId: UUID,
    val state: LiveDashboardMatchState,
    val competitionId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val executionOrder: Int,
    val startTime: LocalDateTime?,
    val currentlyRunning: Boolean,
    val elapsedMinutes: Long?,
    val teams: List<LiveDashboardTeamDto>,
)

data class LiveDashboardDto(
    val matches: List<LiveDashboardMatchDto>,
)
