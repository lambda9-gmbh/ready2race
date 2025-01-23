package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.plugins.StructuredValidationResult
import de.lambda9.ready2race.backend.plugins.Validatable

data class EventRequest(
    val properties: EventProperties
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}