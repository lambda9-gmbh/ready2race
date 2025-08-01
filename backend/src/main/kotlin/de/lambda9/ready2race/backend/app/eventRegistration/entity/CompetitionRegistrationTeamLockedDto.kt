package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class CompetitionRegistrationTeamLockedDto(
    val id: UUID,
    val optionalFees: List<UUID>,
    val namedParticipants: List<CompetitionRegistrationNamedParticipantLockedDto>
)
