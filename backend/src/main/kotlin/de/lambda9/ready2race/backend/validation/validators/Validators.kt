package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.*

object Validators {
    fun <A: Any?> notNull() = Validator<A?> {
        if (it == null) {
            StructuredValidationResult.Invalid.Message { "is null" }
        } else {
            StructuredValidationResult.Valid
        }
    }

    fun <V: Validatable?> selfValidator() = Validator<V> { it?.validate() ?: StructuredValidationResult.Valid }

    fun <T> allOf(vararg validators: Validator<T>) = Validator<T> { value ->
        validators.map { it(value) }.allOf()
    }
    fun <T> oneOf(vararg validators: Validator<T>) = Validator<T> { value ->
        validators.map { it(value) }.oneOf()
    }

    fun <T, C : Collection<T?>> collection(validator: Validator<T?>) = Validator<C?> { collection ->
        collection?.mapIndexed { index, item -> index to validator(item) }
            ?.filterIsInstance<Pair<Int, StructuredValidationResult.Invalid>>()
            ?.takeIf { it.isNotEmpty() }
            ?.let { StructuredValidationResult.Invalid.BadCollectionElements(it.toMap()) }
            ?: StructuredValidationResult.Valid
    }

    val collection get() = collection(selfValidator())
}

