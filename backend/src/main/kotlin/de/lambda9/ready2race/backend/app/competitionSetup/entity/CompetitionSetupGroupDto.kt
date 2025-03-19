package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class CompetitionSetupGroupDto(
    val duplicatable: Boolean,
    val weighting: Int,
    val teams: Int?,
    val name: String?,
    val matches: List<CompetitionSetupMatchDto>,
    val outcomes: List<Int>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: validate

    companion object {
        val example
            get() = CompetitionSetupGroupDto(
                duplicatable = false,
                weighting = 1,
                teams = 4,
                name = "Group name",
                matches = listOf(CompetitionSetupMatchDto.example),
                outcomes = listOf(1, 8, 9, 16)
            )
    }
}