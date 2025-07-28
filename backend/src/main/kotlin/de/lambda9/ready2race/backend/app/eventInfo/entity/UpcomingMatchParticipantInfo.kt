package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

data class UpcomingMatchParticipantInfo(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val namedRole: String?,
    val year: Int?,
    val gender: String?,
    val externalClubName: String?
)