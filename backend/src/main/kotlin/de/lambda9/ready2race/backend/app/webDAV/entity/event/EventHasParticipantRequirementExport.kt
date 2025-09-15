package de.lambda9.ready2race.backend.app.webDAV.entity.event

import java.time.LocalDateTime
import java.util.*

data class EventHasParticipantRequirementExport(
    val event: UUID,
    val participantRequirement: UUID,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val namedParticipant: UUID?,
    val qrCodeRequired: Boolean?
)