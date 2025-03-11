package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class EventRegistrationCompetitionDto(
    val id: UUID,
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val competitionCategory: String?,
    val namedParticipant: List<EventRegistrationNamedParticipantDto>?,
    val fees: List<EventRegistrationFeeDto>,
    val days: List<UUID>
)