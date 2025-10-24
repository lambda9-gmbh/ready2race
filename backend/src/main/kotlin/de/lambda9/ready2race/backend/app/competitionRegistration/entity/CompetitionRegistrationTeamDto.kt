package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementDto
import java.util.*

data class CompetitionRegistrationTeamDto(
    val id: UUID,
    val name: String?,
    val clubId: UUID,
    val clubName: String,
    val namedParticipants: List<CompetitionRegistrationTeamNamedParticipantDto>,
    val deregistration: CompetitionDeregistrationDto?,
    val globalParticipantRequirements: List<ParticipantRequirementDto>,
    val challengeResult: Int?
)