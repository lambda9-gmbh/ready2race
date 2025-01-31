package de.lambda9.ready2race.backend.validation

interface Validatable {
    fun validate(): StructuredValidationResult
// todo: evaluate necessity for Validation Config in .env containing configuration for min password length or similar stuff
}