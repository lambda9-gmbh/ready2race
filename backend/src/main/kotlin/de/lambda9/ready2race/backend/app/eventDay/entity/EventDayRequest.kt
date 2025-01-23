package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable

data class EventDayRequest(
    val properties: EventDayProperties,
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}