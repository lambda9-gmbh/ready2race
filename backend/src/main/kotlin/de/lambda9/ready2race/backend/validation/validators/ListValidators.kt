package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.StructuredValidationResult

object ListValidators {
    val notEmpty get() = Validator<List<Any>?> {
        if(it != null && it.isEmpty()) {
            StructuredValidationResult.Invalid.Message { "is empty" }
        } else {
            StructuredValidationResult.Valid
        }
    }
}