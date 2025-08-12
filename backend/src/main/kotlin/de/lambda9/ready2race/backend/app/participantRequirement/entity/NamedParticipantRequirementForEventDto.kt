package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.UUID

data class NamedParticipantRequirementForEventDto(
    val id: UUID,
    val name: String,
    val qrCodeRequired: Boolean,
)
