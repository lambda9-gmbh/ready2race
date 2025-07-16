package de.lambda9.ready2race.backend.app.teamTracking.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender

data class TeamParticipantDto(
    val participantId: String,
    val firstname: String,
    val lastname: String,
    val year: Int,
    val gender: Gender,
    val role: String?
)