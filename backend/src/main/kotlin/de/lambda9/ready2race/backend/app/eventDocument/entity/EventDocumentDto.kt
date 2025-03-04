package de.lambda9.ready2race.backend.app.eventDocument.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeDto
import java.time.LocalDateTime
import java.util.UUID

data class EventDocumentDto(
    val id: UUID,
    val documentType: EventDocumentTypeDto?,
    val name: String,
    val createdAt: LocalDateTime,
    val createdBy: AppUserNameDto?,
    val updatedAt: LocalDateTime,
    val updatedBy: AppUserNameDto?,
)
