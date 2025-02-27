package de.lambda9.ready2race.backend.app.eventDocument.entity

import de.lambda9.ready2race.backend.app.appuser.entity.CreatedByDto
import java.time.LocalDateTime
import java.util.UUID

data class EventDocumentDto(
    val id: UUID,
    val documentType: String?,
    val name: String,
    val createdAt: LocalDateTime,
    val createdBy: CreatedByDto?
)
