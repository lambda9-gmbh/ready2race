package de.lambda9.ready2race.backend.app.eventRegistration.entity

data class EventRegistrationTemplateDto(
    val name: String,
    val description: String?,
    val location: String?,
    val days: List<EventRegistrationDayDto>,
    val competitionsSingle: List<EventRegistrationCompetitionDto>,
    val competitionsTeam: List<EventRegistrationCompetitionDto>
)

