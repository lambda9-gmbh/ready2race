package de.lambda9.ready2race.backend.app.substitution.entity

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementDto
import java.util.*

data class PossibleSubstitutionParticipantDto(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val external: Boolean?,
    val externalClubName: String?,
    val registrationId: UUID?,
    val registrationName: String?,
    val namedParticipantId: UUID?,
    val namedParticipantName: String?,
    val participantRequirements: List<ParticipantRequirementDto>
)