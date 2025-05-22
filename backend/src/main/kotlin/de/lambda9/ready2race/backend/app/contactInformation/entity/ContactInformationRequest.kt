package de.lambda9.ready2race.backend.app.contactInformation.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class ContactInformationRequest(
    val name: String,
    val addressZip: String,
    val addressStreet: String,
    val email: String,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: @Incomplete

    companion object {
        val example get() = ContactInformationRequest(
            name = "Corp.",
            addressZip = "12345",
            addressStreet = "Gardenstreet 32",
            email = "example@corp.com"
        )
    }
}
