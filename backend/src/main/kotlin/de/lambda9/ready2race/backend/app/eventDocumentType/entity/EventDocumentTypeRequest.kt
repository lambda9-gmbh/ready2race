package de.lambda9.ready2race.backend.app.eventDocumentType.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class EventDocumentTypeRequest(
    val name: String,
    val description: String?,
    val required: Boolean,
    val confirmationRequired: Boolean,
): Validatable {
    override fun validate(): ValidationResult =
        this::name validate notBlank

    companion object {
        val example get() = EventDocumentTypeRequest(
            name = "Wettkampfregeln",
            description = null,
            required = true,
            confirmationRequired = true,
        )
    }
}
