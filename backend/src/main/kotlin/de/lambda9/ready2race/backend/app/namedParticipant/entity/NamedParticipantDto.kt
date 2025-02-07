package de.lambda9.ready2race.backend.app.namedParticipant.entity

import java.util.UUID

data class NamedParticipantDto(
    val id: UUID,
    val name: String,
    val description: String?,
)