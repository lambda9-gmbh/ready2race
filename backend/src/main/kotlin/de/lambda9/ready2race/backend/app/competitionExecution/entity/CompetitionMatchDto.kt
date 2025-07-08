package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.time.LocalDateTime
import java.util.UUID

data class CompetitionMatchDto(
    val id: UUID,
    val name: String?,
    val teams: List<CompetitionMatchTeamDto>,
    val weighting: Int,
    val executionOrder: Int,
    val startTime: LocalDateTime?,
    val startTimeOffset: Long?,
)