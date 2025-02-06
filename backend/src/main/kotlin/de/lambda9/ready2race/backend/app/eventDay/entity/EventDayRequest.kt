package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate

data class EventDayRequest(
    val properties: EventDayProperties,
) : Validatable {
    override fun validate(): ValidationResult =
        this::properties.validate()

    companion object {
        val example
            get() = EventDayRequest(
                properties = EventDayProperties.example
            )
    }
}