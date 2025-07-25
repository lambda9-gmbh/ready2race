package de.lambda9.ready2race.backend.app.competitionDeregistration.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class CompetitionDeregistrationRequest(
    val reason: String?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::reason validate notBlank,
    )

    companion object {
        val example
            get() = CompetitionDeregistrationRequest(
                reason = "DNS"
            )
    }
}