package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.plugins.requestvalidation.*

data class EventRequest(
    val properties: EventProperties
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: test(this::properties)
}