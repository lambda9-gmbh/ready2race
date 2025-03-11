package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class EventRegistrationUpsertDto(
    val participants: List<EventRegistrationParticipantUpsertDto>,
    val competitionRegistrations: List<CompetitionRegistrationUpsertDto>,
    val message: String?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.Valid

    companion object {
        val example
            get() = EventRegistrationUpsertDto(
                participants = emptyList(),
                competitionRegistrations = emptyList(),
                message = null
            )
    }
}