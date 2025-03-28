package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementReducedDto
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.util.*

data class ParticipantForEventDto(
    val id: UUID,
    val clubId: UUID,
    val clubName: String,
    val firstname: String,
    val lastname: String,
    val year: Int?,
    val gender: Gender,
    val external: Boolean?,
    val externalClubName: String?,
    val participantRequirementsChecked: List<ParticipantRequirementReducedDto>?,
)