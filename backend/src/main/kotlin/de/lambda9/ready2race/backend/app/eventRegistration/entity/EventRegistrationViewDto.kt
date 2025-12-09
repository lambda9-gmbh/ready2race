package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.time.LocalDateTime
import java.util.*

data class EventRegistrationViewDto(
    val id: UUID,
    val createdAt: LocalDateTime,
    val message: String?,
    val updatedAt: LocalDateTime,
    val eventId: UUID,
    val eventName: String,
    val clubId: UUID,
    val clubName: String,
    val competitionRegistrationCount: Long,
    val participantCount: Long,
    val eventDocumentsOfficiallyAccepted: Boolean,
)