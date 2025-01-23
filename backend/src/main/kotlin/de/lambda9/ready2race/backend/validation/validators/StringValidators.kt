package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.StructuredValidationResult

object StringValidators {
    val notBlank get() = Validator<String?> {
        if (it?.isBlank() == true) {
            StructuredValidationResult.Invalid.Message { "is blank" }
        } else {
            StructuredValidationResult.Valid
        }
    }

    fun pattern(regex: Regex) = Validator<String?> { value ->
        if (value == null || regex.matches(value)) {
            StructuredValidationResult.Valid
        } else {
            StructuredValidationResult.Invalid.PatternMismatch(value, regex)
        }
    }
}