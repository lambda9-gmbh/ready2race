package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.time.LocalDateTime

data class EventProperties (
    val name: String,
    val description: String?,
    val location: String?,
    val registrationAvailableFrom: LocalDateTime?,
    val registrationAvailableTo: LocalDateTime?,
    val invoicePrefix: String?,
): Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::name validate notBlank,
            this::description validate notBlank,
            this::location validate notBlank,
            this::invoicePrefix validate notBlank,
        )
    companion object{
        val example get() = EventProperties(
            name = "Name",
            description = "Description",
            location = "Town",
            registrationAvailableFrom = LocalDateTime.now(),
            registrationAvailableTo = LocalDateTime.now().plusDays(7),
            invoicePrefix = "Invoice-Prefix"
        )
    }}