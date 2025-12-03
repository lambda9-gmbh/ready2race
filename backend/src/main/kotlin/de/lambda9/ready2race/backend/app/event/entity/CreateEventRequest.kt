package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.time.LocalDate
import java.time.LocalDateTime

data class CreateEventRequest(
    val name: String,
    val description: String?,
    val location: String?,
    val registrationAvailableFrom: LocalDateTime?,
    val registrationAvailableTo: LocalDateTime?,
    val lateRegistrationAvailableTo: LocalDateTime?,
    val invoicePrefix: String?,
    val published: Boolean,
    val paymentDueBy: LocalDate?,
    val latePaymentDueBy: LocalDate?,
    val mixedTeamTerm: String?,
    val challengeEvent: Boolean,
    val challengeResultType: MatchResultType?,
    val allowSelfSubmission: Boolean,
    val allowParticipantSelfRegistration: Boolean,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
            this::description validate notBlank,
            this::location validate notBlank,
            this::invoicePrefix validate notBlank,
            this::mixedTeamTerm validate notBlank,
            ValidationResult.oneOf(
                ValidationResult.allOf(
                    this::challengeEvent validate isValue(true),
                    this::challengeResultType validate notNull
                ),
                ValidationResult.allOf(
                    this::challengeEvent validate isValue(false),
                    this::challengeResultType validate isNull
                )
            )
        )

    companion object {
        val example
            get() = CreateEventRequest(
                name = "Name",
                description = "Description",
                location = "Town",
                registrationAvailableFrom = LocalDateTime.now(),
                registrationAvailableTo = LocalDateTime.now().plusDays(7),
                lateRegistrationAvailableTo = LocalDateTime.now().plusDays(21),
                invoicePrefix = "Invoice-Prefix",
                published = false,
                paymentDueBy = LocalDate.now().plusDays(14),
                latePaymentDueBy = LocalDate.now().minusDays(28),
                mixedTeamTerm = "Renngemeinschaft",
                challengeEvent = true,
                challengeResultType = MatchResultType.DISTANCE,
                allowSelfSubmission = false,
                allowParticipantSelfRegistration = false,
            )
    }
}