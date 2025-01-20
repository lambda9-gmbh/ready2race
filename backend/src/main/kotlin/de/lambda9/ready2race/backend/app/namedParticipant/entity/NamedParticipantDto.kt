package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.app.participantCount.entity.ParticipantCountDto

data class NamedParticipantDto(
    val name: String,
    val description: String?,
    val required: Boolean,
    val participantCount: ParticipantCountDto
)