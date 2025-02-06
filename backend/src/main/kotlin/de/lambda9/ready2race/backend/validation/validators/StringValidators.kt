package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.ValidationResult

object StringValidators : Validators<String?>() {
    val notBlank
        get() = simple("is blank") { it.isNotBlank() }

    fun pattern(regex: Regex) = Validator<String?> { value ->
        if (value != null && !regex.matches(value)) {
            ValidationResult.Invalid.PatternMismatch(value, regex)
        } else {
            ValidationResult.Valid
        }
    }

    fun maxLength(max: Int) = simple("is too long") { it.length <= max }

    fun minLength(min: Int) = simple("is too short") { it.length >= min }
}