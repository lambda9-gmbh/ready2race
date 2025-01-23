package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate

data class EventRequest(
    val properties: EventProperties
): Validatable {
    override fun validate(): StructuredValidationResult =
        this::properties.validate()
}