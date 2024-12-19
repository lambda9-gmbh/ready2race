package de.lambda9.ready2race.backend.validation

import io.ktor.server.plugins.requestvalidation.*

interface Validatable {

    fun validate(): ValidationResult
}