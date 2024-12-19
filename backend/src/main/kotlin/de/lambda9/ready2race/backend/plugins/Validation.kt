package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*

fun Application.configureValidation() {
    install(RequestValidation) {
        validate<Validatable> { validatable ->
            validatable.validate()
        }
    }
}