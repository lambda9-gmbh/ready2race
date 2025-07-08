package de.lambda9.ready2race.backend.app.competitionExecution.entity

data class CompetitionRoundDto(
    val name: String,
    val matches: List<CompetitionMatchDto>,
    val required: Boolean,
)