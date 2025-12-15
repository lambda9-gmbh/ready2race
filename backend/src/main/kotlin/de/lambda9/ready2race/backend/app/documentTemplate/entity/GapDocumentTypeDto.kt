package de.lambda9.ready2race.backend.app.documentTemplate.entity

data class GapDocumentTypeDto(
    val type: GapDocumentType,
    val assignedTemplate: AssignedTemplateId?,
)
