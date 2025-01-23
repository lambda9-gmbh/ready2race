package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import java.time.LocalDate

data class EventDayProperties(
    val date: LocalDate,
    val name: String?,
    val description: String?,
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}