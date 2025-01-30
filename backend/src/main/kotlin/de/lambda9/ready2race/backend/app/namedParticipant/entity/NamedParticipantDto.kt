package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class NamedParticipantDto(
    val name: String,
    val description: String?,
) : Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::name validate notBlank,
            this::description validate notBlank
        )

    companion object {
        val example get() = NamedParticipantDto(
                name = "Name",
                description = "Description",
            )
    }
}