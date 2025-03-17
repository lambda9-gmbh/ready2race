package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.allOf
import de.lambda9.ready2race.backend.validation.anyOf
import kotlin.reflect.KProperty0

fun interface Validator<T> {
    operator fun invoke(t: T): ValidationResult
    operator fun invoke(prop: KProperty0<T>): ValidationResult = invoke(prop())

    companion object {
        val notNull
            get() = Validator<Any?> {
                if (it == null) {
                    ValidationResult.Invalid.Message { "is null" }
                } else {
                    ValidationResult.Valid
                }
            }

        fun <T> simple(message: String, valid: (T & Any) -> Boolean) = Validator<T> { value ->
            if (value != null && !valid(value)) {
                ValidationResult.Invalid.Message { message }
            } else {
                ValidationResult.Valid
            }
        }

        val selfValidator get() = Validator<Validatable?> { it?.validate() ?: ValidationResult.Valid }

        fun <T> allOf(vararg validators: Validator<in T>) = Validator<T> { value ->
            validators.map { it(value) }.allOf()
        }

        fun <T> anyOf(vararg validators: Validator<in T>) = Validator<T> { value ->
            validators.map { it(value) }.anyOf()
        }

        @Suppress("UNCHECKED_CAST")
        fun <T, C : Collection<T>?> collection(validator: Validator<T>) = Validator<C> { collection ->
            (
                collection?.mapIndexed { index, item -> index to validator(item) }
                    ?.filter { it.second is ValidationResult.Invalid }
                    as List<Pair<Int, ValidationResult.Invalid>>?
                )
                ?.takeIf { it.isNotEmpty() }
                ?.let { ValidationResult.Invalid.BadCollectionElements((it).toMap()) }
                ?: ValidationResult.Valid
        }

        val collection get() = collection(selfValidator)
    }
}