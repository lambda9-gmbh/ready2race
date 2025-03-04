package de.lambda9.ready2race.backend.app.eventDocument.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

data class EventDocumentRequest(
    val documentType: UUID?
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = EventDocumentRequest(
            documentType = UUID.randomUUID()
        )
    }
}
