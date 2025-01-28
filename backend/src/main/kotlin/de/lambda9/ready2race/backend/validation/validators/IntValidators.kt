package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.StructuredValidationResult

object IntValidators {
    val notNegative get() = Validator<Int?> {
        if (it != null && it < 0) {
            StructuredValidationResult.Invalid.Message { "is negative" }
        } else {
            StructuredValidationResult.Valid
        }
    }

    fun min(min: Int) = Validator<Int?> {
        if (it != null && it < min) {
            StructuredValidationResult.Invalid.Message { "is less than $min" }
        } else {
            StructuredValidationResult.Valid
        }
    }
}
