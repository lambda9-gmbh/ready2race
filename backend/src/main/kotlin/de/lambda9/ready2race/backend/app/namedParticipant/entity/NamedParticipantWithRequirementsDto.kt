package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.app.participantRequirement.entity.EventParticipantRequirementDto
import java.util.UUID

data class NamedParticipantWithRequirementsDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val requirements: List<EventParticipantRequirementDto>,
    val qrCodeRequired: Boolean
)