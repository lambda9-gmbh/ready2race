package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionDto

data class CompetitionRoundDto(
    val name: String,
    val matches: List<CompetitionMatchDto>,
    val required: Boolean,
    val substitutions: List<SubstitutionDto>
)