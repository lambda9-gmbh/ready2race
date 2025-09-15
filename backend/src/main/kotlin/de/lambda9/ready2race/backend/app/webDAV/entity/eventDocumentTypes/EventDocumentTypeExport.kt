package de.lambda9.ready2race.backend.app.webDAV.entity.eventDocumentTypes

import java.time.LocalDateTime
import java.util.*

data class EventDocumentTypeExport(
    val id: UUID,
    val name: String,
    val required: Boolean,
    val confirmationRequired: Boolean,
    val description: String?,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)