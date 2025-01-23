package de.lambda9.ready2race.backend.validation

interface Validatable {
    fun validate(): StructuredValidationResult
}