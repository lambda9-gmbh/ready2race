package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class CompetitionSetupRoundDto(
    val name: String,
    val required: Boolean,
    val matches: List<CompetitionSetupMatchDto>?,
    val groups: List<CompetitionSetupGroupDto>?,
    val statisticEvaluations: List<CompetitionSetupGroupStatisticEvaluationDto>?,
    val useDefaultSeeding: Boolean
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: validate

    companion object {
        val example
            get() = CompetitionSetupRoundDto(
                name = "Round name",
                required = false,
                matches = listOf(CompetitionSetupMatchDto.example), // todo: should provide 2 examples (one with matches, one with groups) or extra details/description
                groups = listOf(CompetitionSetupGroupDto.example),
                statisticEvaluations = listOf(CompetitionSetupGroupStatisticEvaluationDto.example),
                useDefaultSeeding = true
            )
    }
}