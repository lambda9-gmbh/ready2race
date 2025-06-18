package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class CompetitionSetupGroupStatisticEvaluationDto(
    val name: String,
    val priority: Int,
    val rankByBiggest: Boolean,
    val ignoreBiggestValues: Int,
    val ignoreSmallestValues: Int,
    val asAverage: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
    )

    companion object {
        val example
            get() = CompetitionSetupGroupStatisticEvaluationDto(
                name = "Points",
                priority = 1,
                rankByBiggest = true,
                ignoreBiggestValues = 0,
                ignoreSmallestValues = 0,
                asAverage = false,
            )
    }
}