package de.lambda9.ready2race.backend.app.eventRegistration.entity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull

@JsonIgnoreProperties(ignoreUnknown = true)
data class EventRegistrationUpsertDto(
    val participants: List<EventRegistrationParticipantUpsertDto>,
    val competitionRegistrations: List<CompetitionRegistrationUpsertDto>,
    val message: String?,
    val callbackUrl: String? = null, // Is only allowed to be null internally in the server
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.oneOf(
            this::callbackUrl validate notNull,
            ValidationResult.allOf(
                this::callbackUrl validate isNull,
                this::participants validate CollectionValidators.isEmpty,
                this::competitionRegistrations validate CollectionValidators.isEmpty,
            )
        )

    companion object {
        val example
            get() = EventRegistrationUpsertDto(
                participants = emptyList(),
                competitionRegistrations = emptyList(),
                message = null,
                callbackUrl = "https://ready2race.info/challenge/",
            )
    }
}