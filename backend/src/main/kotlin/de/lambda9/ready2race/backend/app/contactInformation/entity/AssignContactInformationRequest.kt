package de.lambda9.ready2race.backend.app.contactInformation.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

data class AssignContactInformationRequest(
    val contact: UUID?,
    val event: UUID?,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = AssignContactInformationRequest(
            contact = UUID.randomUUID(),
            event = UUID.randomUUID(),
        )
    }
}
