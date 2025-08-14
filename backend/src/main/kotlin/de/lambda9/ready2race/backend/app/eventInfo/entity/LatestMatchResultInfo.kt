package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class LatestMatchResultInfo(
    val matchId: UUID,
    val competitionId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val matchNumber: Int?,
    val updatedAt: LocalDateTime,
    val eventDayId: UUID?,
    val eventDayDate: LocalDate?,
    val eventDayName: String?,
    val startTime: LocalDateTime?,
    val teams: List<MatchResultTeamInfo>
)

data class MatchResultTeamInfo(
    val teamId: UUID,
    val teamName: String?,
    val teamNumber: Int?,
    val clubName: String?,
    val place: Int,
    val participants: List<ParticipantInfo>
)