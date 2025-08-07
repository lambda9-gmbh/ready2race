package de.lambda9.ready2race.backend.app.eventRegistration.entity

data class EventRegistrationLockedDto(
    val participants: List<EventRegistrationParticipantLockedDto>,
    val competitionRegistrations: List<CompetitionRegistrationLockedDto>,
)
