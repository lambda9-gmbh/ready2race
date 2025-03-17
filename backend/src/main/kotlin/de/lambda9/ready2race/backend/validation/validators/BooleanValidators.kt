package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.ValidationResult

object BooleanValidators : Validators<Boolean?>() {
    val isTrue
        get() = Validator<Boolean?> { value ->
            if (value == null || !value) {
                ValidationResult.Invalid.Message { "is not true" }
            } else {
                ValidationResult.Valid
            }
        }

    val isFalseOrNull
        get() = simple("is not false or null") { !it }

}
