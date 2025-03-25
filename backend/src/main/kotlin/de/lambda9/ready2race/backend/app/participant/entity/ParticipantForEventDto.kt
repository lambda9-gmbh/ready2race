package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.util.*

data class ParticipantForEventDto(
    val clubId: UUID,
    val clubName: String,
    val participantId: UUID,
    val firstname: String,
    val lastname: String,
    val year: Int?,
    val gender: Gender,
    val external: Boolean?,
    val externalClubName: String?,
    val participantRequirementsChecked: Array<String?>?,
)