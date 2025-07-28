package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class ParticipantRequirementUpsertDto(
    val name: String,
    val description: String?,
    val optional: Boolean?,
    val checkInApp: Boolean?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
    )

    companion object {
        val example
            get() = ParticipantRequirementUpsertDto(
                name = "Name",
                description = "Description",
                optional = false,
                checkInApp = false,
            )
    }
}
