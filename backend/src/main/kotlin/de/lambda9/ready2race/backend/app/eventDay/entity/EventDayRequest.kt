package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.plugins.StructuredValidationResult
import de.lambda9.ready2race.backend.plugins.Validatable

data class EventDayRequest(
    val properties: EventDayProperties,
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}