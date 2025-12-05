package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class GapDocumentTemplateRequest(
    val type: GapDocumentType,
    val placeholders: List<GapDocumentPlaceholderRequest>,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = GapDocumentTemplateRequest(
            type = GapDocumentType.CERTIFICATE_OF_PARTICIPATION,
            placeholders = emptyList(),
        )
    }
}
