package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.app.participantRequirement.entity.CompetitionRegistrationNamedParticipantRequirementDto
import java.util.*

data class CompetitionRegistrationTeamNamedParticipantDto(
    val namedParticipantId: UUID,
    val namedParticipantName: String,
    val participants: List<ParticipantForCompetitionRegistrationTeam>,
    val participantRequirements: List<CompetitionRegistrationNamedParticipantRequirementDto>
)

