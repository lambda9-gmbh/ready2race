package de.lambda9.ready2race.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*

interface Validatable {

    fun validate(): ValidationResult
}

fun Application.configureValidation() {

    install(RequestValidation) {
        validate<Validatable> { validatable ->
            validatable.validate()
        }
    }
}

fun Collection<Validatable>.validate(): ValidationResult =
    map { it.validate() }.filterIsInstance<ValidationResult.Invalid>().let { invalids ->
        if (invalids.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(
                invalids.flatMap { invalid -> invalid.reasons }
            )
        }
    }