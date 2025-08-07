package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class EventRegistrationParticipantLockedDto(
    val id: UUID,
    val competitionsSingle: List<CompetitionRegistrationSingleLockedDto>
)
