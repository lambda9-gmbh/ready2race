package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.UUID

data class CheckedParticipantRequirement(
    val id: UUID,
    val note: String?
)
