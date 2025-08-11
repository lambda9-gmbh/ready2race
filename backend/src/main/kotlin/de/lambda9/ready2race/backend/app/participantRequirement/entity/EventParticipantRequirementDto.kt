package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.UUID

data class EventParticipantRequirementDto(
    val requirementId: UUID,
    val requirementName: String,
    val requirementDescription: String?,
    val namedParticipantId: UUID?,
    val qrCodeRequired: Boolean
)