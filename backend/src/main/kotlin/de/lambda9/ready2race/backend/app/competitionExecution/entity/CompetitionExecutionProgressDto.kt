package de.lambda9.ready2race.backend.app.competitionExecution.entity

data class CompetitionExecutionProgressDto(
    val rounds: List<CompetitionRoundDto>,
    val lastRoundFinished: Boolean,
    val canCreateNewRound: Boolean
)