package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.maxLength
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validators.allOf
import java.time.LocalDateTime

data class EventProperties (
    val name: String,
    val description: String?,
    val location: String?,
    val registrationAvailableFrom: LocalDateTime?,
    val registrationAvailableTo: LocalDateTime?,
    val paymentDueDate: LocalDateTime?,
    val invoicePrefix: String?,
): Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::name validate allOf(notBlank, maxLength(100)), // todo maxLength for every name?
            this::description validate notBlank,
            this::location validate notBlank,
            // todo: payment due date after reg.AvailableTo/From
            this::invoicePrefix validate allOf(notBlank, maxLength(32)), // todo keep maxLength check?
        )
    companion object{
        val example get() = EventProperties(
            name = "Name",
            description = "Description",
            location = "Town",
            registrationAvailableFrom = LocalDateTime.now(),
            registrationAvailableTo = LocalDateTime.now().plusDays(7),
            paymentDueDate = LocalDateTime.now().plusDays(8),
            invoicePrefix = "Invoice-Prefix"
        )
    }}