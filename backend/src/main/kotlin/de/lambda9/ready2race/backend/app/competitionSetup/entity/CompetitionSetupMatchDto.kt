package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection

data class CompetitionSetupMatchDto(
    val weighting: Int,
    val teams: Int?,
    val name: String?,
    val participants: List<Int>,
    val executionOrder: Int,
    val startTimeOffset: Long?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::weighting validate min(1),
        this::teams validate min(1),
        this::name validate notBlank,
        this::participants validate collection(min(1)),
        this::executionOrder validate min(1)
    )

    companion object {
        val example
            get() = CompetitionSetupMatchDto(
                weighting = 1,
                teams = 2,
                name = "Match name",
                participants = listOf(1, 8),
                executionOrder = 1,
                startTimeOffset = 60000
            )
    }
}