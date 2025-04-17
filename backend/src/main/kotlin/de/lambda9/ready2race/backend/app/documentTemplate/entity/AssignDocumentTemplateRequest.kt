package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

data class AssignDocumentTemplateRequest(
    val template: UUID,
    val event: UUID?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = AssignDocumentTemplateRequest(
            template = UUID.randomUUID(),
            event = null,
        )
    }
}
