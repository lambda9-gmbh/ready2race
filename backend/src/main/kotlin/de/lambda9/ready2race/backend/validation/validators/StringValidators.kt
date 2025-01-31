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
        if (value != null && !regex.matches(value)) {
            StructuredValidationResult.Invalid.PatternMismatch(value, regex)
        } else {
            StructuredValidationResult.Valid
        }
    }

    fun maxLength(max: Int) = Validator<String?> { value ->
        if(value != null && value.length > max) {
            StructuredValidationResult.Invalid.Message { "is too long" }
        } else {
            StructuredValidationResult.Valid
        }
    }

    fun minLength(min: Int) = Validator<String?> { value ->
        if (value != null && value.length < min) {
            StructuredValidationResult.Invalid.Message { "is too short" }
        } else {
            StructuredValidationResult.Valid
        }
    }
}