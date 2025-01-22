package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.plugins.Validatable
import io.ktor.server.plugins.requestvalidation.*
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
    override fun validate(): ValidationResult = ValidationResult.Valid
}