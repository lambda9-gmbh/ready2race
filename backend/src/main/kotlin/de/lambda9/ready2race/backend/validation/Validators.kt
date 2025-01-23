package de.lambda9.ready2race.backend.validation

class Validators<T: Any?> {
    val notNull = Validator<T?> {
        if (it == null) {
            StructuredValidationResult.Invalid.Message { "is null" }
        } else {
            StructuredValidationResult.Valid
        }
    }

    companion object {
        fun <V: Validatable?> selfValidator() = Validator<V> { it?.validate() ?: StructuredValidationResult.Valid }

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

        fun <T> collection(validator: Validator<T>) = Validator<Collection<T>?> { collection ->
            collection?.mapIndexed { index, item -> index to validator(item)}
                ?.filterIsInstance<Pair<Int, StructuredValidationResult.Invalid>>()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    StructuredValidationResult.Invalid.BadCollectionElements(it.toMap())
                }
                ?: StructuredValidationResult.Valid
        }

        val collection get() = collection(selfValidator())

        fun <T> allOf(vararg validators: Validator<T>) = Validator<T> { value ->
            validators.map { it(value) }.allOf()
        }
        fun <T> oneOf(vararg validators: Validator<T>) = Validator<T> { value ->
            validators.map { it(value) }.oneOf()
        }
    }
}