package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.UUID

data class NamedParticipantRequirmentForEventDto(
    val id: UUID,
    val name: String,
    val qrCodeRequired: Boolean,
    val event: UUID,
    val participantRequirement: UUID
)
