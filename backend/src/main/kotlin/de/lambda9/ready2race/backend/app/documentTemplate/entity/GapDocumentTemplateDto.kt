package de.lambda9.ready2race.backend.app.documentTemplate.entity

import java.util.UUID

data class GapDocumentTemplateDto(
    val id: UUID,
    val type: GapDocumentType,
    val name: String,
    val placeholders: List<GapDocumentPlaceholderDto>,
)
