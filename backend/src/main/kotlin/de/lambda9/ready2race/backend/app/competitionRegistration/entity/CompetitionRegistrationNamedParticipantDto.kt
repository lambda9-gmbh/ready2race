package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventDto
import java.util.*

data class CompetitionRegistrationNamedParticipantDto(
    val namedParticipantId: UUID,
    val namedParticipantName: String,
    val participants: List<ParticipantForEventDto>,
)

