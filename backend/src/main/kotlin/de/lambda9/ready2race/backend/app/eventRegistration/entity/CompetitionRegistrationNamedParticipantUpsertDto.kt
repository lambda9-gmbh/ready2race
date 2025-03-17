package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class CompetitionRegistrationNamedParticipantUpsertDto (
    val namedParticipantId: UUID,
    val participantIds: List<UUID>,
)