package de.lambda9.ready2race.backend.app.substitution.entity

import java.util.UUID

data class ParticipantForExecutionDto(
    val id: UUID,
    val namedParticipantId: UUID,
    val namedParticipantName: String,
    val firstName: String,
    val lastName: String,
    val clubId: UUID,
    val clubName: String,
    val competitionRegistrationId: UUID,
    val competitionRegistrationName: String?,
    val external: Boolean?,
    val externalClubName: String?
)