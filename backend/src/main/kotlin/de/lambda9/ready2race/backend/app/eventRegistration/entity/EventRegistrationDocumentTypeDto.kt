package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class EventRegistrationDocumentTypeDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val confirmationRequired: Boolean,
    val files: List<EventRegistrationDocumentFileDto>
)