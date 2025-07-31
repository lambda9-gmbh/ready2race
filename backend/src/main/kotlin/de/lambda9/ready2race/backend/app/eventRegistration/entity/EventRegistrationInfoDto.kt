package de.lambda9.ready2race.backend.app.eventRegistration.entity

data class EventRegistrationInfoDto(
    val state: OpenForRegistrationType,
    val name: String,
    val description: String?,
    val location: String?,
    val days: List<EventRegistrationDayDto>,
    val documentTypes: List<EventRegistrationDocumentTypeDto>,
    val competitionsSingle: List<EventRegistrationCompetitionDto>,
    val competitionsTeam: List<EventRegistrationCompetitionDto>
)

