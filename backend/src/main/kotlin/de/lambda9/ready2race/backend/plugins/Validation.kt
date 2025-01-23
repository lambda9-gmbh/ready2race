package de.lambda9.ready2race.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import kotlin.reflect.KProperty0

sealed interface StructuredValidationResult {
    data object Valid : StructuredValidationResult
    sealed interface Invalid : StructuredValidationResult {
        data class Field(val field: String, val error: Invalid) : Invalid
        data class BadCollectionElements(val errorPositions: Map<Int, Invalid>) : Invalid
        data class PatternMismatch(val value: String, val pattern: Regex) : Invalid

        data class And(val errors: List<Invalid>) : Invalid
        data class Or(val errors: List<Invalid>) : Invalid

        data object Null : Invalid
        data object Blank : Invalid
    }

    infix fun or(other: StructuredValidationResult) = or(listOf(this, other))
    infix fun and(other: StructuredValidationResult) = and(listOf(this, other))

    fun toValidationResult(): ValidationResult = when (this) {
        is Invalid -> ValidationResult.Invalid(jsonMapper.writeValueAsString(this))
        Valid -> ValidationResult.Valid
    }

    companion object {
        fun or(results: Collection<StructuredValidationResult>) =
            if (results.none { it == Valid }) {
                Invalid.Or(results.filterIsInstance<Invalid>())
            } else {
                Valid
            }
        fun and(results: Collection<StructuredValidationResult>) =
            results.filterIsInstance<Invalid>()
                .takeIf { it.isNotEmpty() }
                ?.let { Invalid.And(it) }
                ?: Valid
    }
}

interface Validatable {

    fun validate(): StructuredValidationResult
}

fun interface Validator<T> {
    operator fun invoke(t: T?): StructuredValidationResult
    operator fun invoke(prop: KProperty0<T?>): StructuredValidationResult = invoke(prop())
}

private val selfValidator = Validator<Validatable?> { it?.validate() ?: StructuredValidationResult.Valid }

val notNull = Validator<Any?> {
    if (it == null) {
        StructuredValidationResult.Invalid.Null
    } else {
        StructuredValidationResult.Valid
    }
}

val notBlank = Validator<String?> {
    if (it?.isBlank() == true) {
        StructuredValidationResult.Invalid.Blank
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

val collection = collection(selfValidator)

infix fun <V> KProperty0<V>.validate(validator: Validator<V>) = when (val result = validator(this)) {
    StructuredValidationResult.Valid -> StructuredValidationResult.Valid
    is StructuredValidationResult.Invalid -> StructuredValidationResult.Invalid.Field(name, result)
}

fun <V: Validatable?> KProperty0<V>.validate() = this validate selfValidator

fun Application.configureValidation() {

    install(RequestValidation) {
        validate<Validatable> { validatable ->
            validatable.validate().toValidationResult()
        }
    }
}
