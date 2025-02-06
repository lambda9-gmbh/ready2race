package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate

data class EventRequest(
    val properties: EventProperties
): Validatable {
    override fun validate(): ValidationResult =
        this::properties.validate()

    companion object{
        val example get() = EventRequest(
            properties = EventProperties.example
        )
    }
}