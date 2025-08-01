package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class RunningMatchInfo(
    val matchId: UUID,
    val matchNumber: Int?,
    val competitionId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val eventDayId: UUID?,
    val eventDayDate: LocalDate?,
    val eventDayName: String?,
    val startTime: LocalDateTime?,
    val elapsedMinutes: Long?,
    val placeName: String?,
    val roundNumber: Int?,
    val roundName: String?,
    val matchName: String?,
    val executionOrder: Int,
    val teams: List<RunningMatchTeamInfo>
)