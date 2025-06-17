package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.time.LocalDateTime

data class CompetitionMatchDto(
    val name: String?,
    val teams: List<CompetitionMatchTeamDto>,
    val weighting: Int,
    val executionOrder: Int,
    val startTime: LocalDateTime?,
    val startTimeOffset: Long?,
)