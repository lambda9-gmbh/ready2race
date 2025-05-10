package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min

data class CompetitionSetupPlaceDto(
    val roundOutcome: Int,
    val place: Int,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::roundOutcome validate min(1),
        this::roundOutcome validate min(1)
    )

    companion object {
        val example
            get() = CompetitionSetupPlaceDto(
                roundOutcome = 7,
                place = 5,
            )
    }
}