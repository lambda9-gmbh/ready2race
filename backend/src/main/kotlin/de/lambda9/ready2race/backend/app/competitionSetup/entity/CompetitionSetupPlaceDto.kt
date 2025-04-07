package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class CompetitionSetupPlaceDto(
    val roundOutcome: Int,
    val place: Int,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: Validate

    companion object {
        val example
            get() = CompetitionSetupPlaceDto(
                roundOutcome = 7,
                place = 5,
            )
    }
}