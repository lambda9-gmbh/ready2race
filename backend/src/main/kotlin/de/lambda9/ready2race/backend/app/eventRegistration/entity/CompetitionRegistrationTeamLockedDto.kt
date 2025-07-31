package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class CompetitionRegistrationTeamLockedDto(
    val optionalFees: List<UUID>,
    val participants: List<CompetitionRegistrationNamedParticipantLockedDto>
)
