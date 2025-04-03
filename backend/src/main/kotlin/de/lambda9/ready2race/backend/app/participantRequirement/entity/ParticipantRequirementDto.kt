package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.*

data class ParticipantRequirementDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
)