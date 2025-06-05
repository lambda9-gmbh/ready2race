package de.lambda9.ready2race.backend.app.eventDocumentType.entity

import java.util.UUID

data class EventDocumentTypeDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val required: Boolean,
    val confirmationRequired: Boolean,
)
