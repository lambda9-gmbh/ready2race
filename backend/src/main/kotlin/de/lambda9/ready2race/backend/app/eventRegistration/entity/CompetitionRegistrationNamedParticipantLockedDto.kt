package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.UUID

data class CompetitionRegistrationNamedParticipantLockedDto(
    val namedParticipantId: UUID,
    val participants: List<UUID>,
)
