package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class CompetitionSetupMatchDto(
    val weighting: Int,
    val teams: Int?,
    val name: String?,
    val outcomes: List<Int>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: validate

    companion object {
        val example
            get() = CompetitionSetupMatchDto(
                weighting = 1,
                teams = 2,
                name = "Match name",
                outcomes = listOf(1, 8),
            )
    }
}