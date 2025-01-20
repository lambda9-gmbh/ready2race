package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.plugins.requestvalidation.*

data class EventDayRequest(
    val properties: EventDayProperties,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: test()
}