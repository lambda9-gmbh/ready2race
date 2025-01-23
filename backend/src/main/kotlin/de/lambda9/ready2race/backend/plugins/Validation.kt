package de.lambda9.ready2race.backend.plugins

import com.fasterxml.jackson.annotation.JsonValue
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import kotlin.reflect.KProperty0



sealed interface StructuredValidationResult {
    data object Valid : StructuredValidationResult
    sealed interface Invalid : StructuredValidationResult {

        fun interface Message : Invalid {
            @JsonValue
            fun message(): String
        }

        data class Field(val field: String, val error: Invalid) : Invalid
        data class BadCollectionElements(val errorPositions: Map<Int, Invalid>) : Invalid
        data class PatternMismatch(val value: String, val pattern: Regex) : Invalid

        data class AllOf(val errors: List<Invalid>) : Invalid
        data class OneOf(val errors: List<Invalid>) : Invalid
    }

    fun toValidationResult(): ValidationResult = when (this) {
        is Invalid -> ValidationResult.Invalid(jsonMapper.writeValueAsString(this))
        Valid -> ValidationResult.Valid
    }

    companion object {
        fun oneOf(vararg results: StructuredValidationResult) =
            if (results.none { it == Valid }) {
                Invalid.OneOf(results.filterIsInstance<Invalid>())
            } else {
                Valid
            }
        fun allOf(vararg results: StructuredValidationResult) =
            results.filterIsInstance<Invalid>()
                .takeIf { it.isNotEmpty() }
                ?.let {
                    if (it.size == 1) {
                        it.first()
                    } else {
                        Invalid.AllOf(it)
                    }
                }
                ?: Valid
    }
}

fun Collection<StructuredValidationResult>.allOf() = StructuredValidationResult.allOf(*this.toTypedArray())
fun Collection<StructuredValidationResult>.oneOf() = StructuredValidationResult.oneOf(*this.toTypedArray())

interface Validatable {
    fun validate(): StructuredValidationResult
}

fun interface Validator<T> {
    operator fun invoke(t: T?): StructuredValidationResult
    operator fun invoke(prop: KProperty0<T?>): StructuredValidationResult = invoke(prop())
}

private fun <V: Validatable?> selfValidator() = Validator<V> { it?.validate() ?: StructuredValidationResult.Valid }

class Validators<T: Any?> {
    val notNull = Validator<T?> {
        if (it == null) {
            StructuredValidationResult.Invalid.Message { "is null" }
        } else {
            StructuredValidationResult.Valid
        }
    }

    companion object {
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

fun <V> KProperty0<V>.validate(validator: Validators<V>.() -> Validator<V?>) = when (val result = validator(Validators())(this)) {
    StructuredValidationResult.Valid -> StructuredValidationResult.Valid
    is StructuredValidationResult.Invalid -> StructuredValidationResult.Invalid.Field(name, result)
}

fun <V: Validatable?> KProperty0<V>.validate() = validate { selfValidator() }

fun Application.configureValidation() {

    install(RequestValidation) {
        validate<Validatable> { validatable ->
            validatable.validate().toValidationResult()
        }
    }
}
