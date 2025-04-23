package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class CompetitionSetupMatchDto(
    val duplicatable: Boolean,
    val weighting: Int,
    val teams: Int?,
    val name: String?,
    val participants: List<Int>,
    val startTimeOffset: Long?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::weighting validate min(1),
        this::teams validate min(1),
        this::name validate notBlank,
    )

    /* todo validations:
        - "participants" values: min(1)
    */

    companion object {
        val example
            get() = CompetitionSetupMatchDto(
                duplicatable = false,
                weighting = 1,
                teams = 2,
                name = "Match name",
                participants = listOf(1, 8),
                startTimeOffset = 60000
            )
    }
}