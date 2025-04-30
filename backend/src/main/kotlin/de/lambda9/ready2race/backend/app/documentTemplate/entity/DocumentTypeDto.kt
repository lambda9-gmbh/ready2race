package de.lambda9.ready2race.backend.app.documentTemplate.entity

import java.util.*

data class DocumentTypeDto(
    val type: DocumentType,
    val assignedTemplate: AssignedTemplateId?,
)
