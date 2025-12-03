package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

data class ParticipantInfo(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val namedRole: String?,
    val externalClubName: String?
)