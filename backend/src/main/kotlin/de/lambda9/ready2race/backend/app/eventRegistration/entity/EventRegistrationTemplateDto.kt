package de.lambda9.ready2race.backend.app.eventRegistration.entity

data class EventRegistrationTemplateDto(
    val name: String,
    val description: String?,
    val location: String?,
    val days: List<EventRegistrationDayDto>,
    val racesSingle: List<EventRegistrationRaceDto>,
    val racesTeam: List<EventRegistrationRaceDto>
)

