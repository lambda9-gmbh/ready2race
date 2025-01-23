package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.*

class Validators<A: Any?> {
    val notNull = Validator<A?> {
        if (it == null) {
            StructuredValidationResult.Invalid.Message { "is null" }
        } else {
            StructuredValidationResult.Valid
        }
    }

    companion object {
        fun <V: Validatable?> selfValidator() = Validator<V> { it?.validate() ?: StructuredValidationResult.Valid }

        fun <T> allOf(vararg validators: Validator<T>) = Validator<T> { value ->
            validators.map { it(value) }.allOf()
        }
        fun <T> oneOf(vararg validators: Validator<T>) = Validator<T> { value ->
            validators.map { it(value) }.oneOf()
        }
    }
}

