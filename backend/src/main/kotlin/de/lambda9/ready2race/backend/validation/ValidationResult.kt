package de.lambda9.ready2race.backend.validation

import com.fasterxml.jackson.annotation.JsonValue

sealed interface ValidationResult {
    data object Valid : ValidationResult // todo: serialize
    sealed interface Invalid : ValidationResult {

        fun interface Message : Invalid {
            @JsonValue
            fun message(): String
        }

        data class Field(val field: String, val error: Invalid) : Invalid
        data class BadCollectionElements(val errorPositions: Map<Int, Invalid>) : Invalid
        data class Duplicate(val value: Any?, val count: Int) : Invalid
        data class Duplicates(val duplicates: List<Duplicate>) : Invalid
        data class PatternMismatch(val value: String, val pattern: Regex) : Invalid

        data class AllOf(val allOf: List<Invalid>) : Invalid
        data class AnyOf(val anyOf: List<Invalid>) : Invalid
        data class OneOf(val oneOf: List<ValidationResult>) : Invalid
    }

    fun <A> fold(
        onValid: () -> A,
        onInvalid: (Invalid) -> A,
    ): A = when (this) {
        is Invalid -> onInvalid(this)
        Valid -> onValid()
    }

    companion object {

        fun anyOf(vararg results: ValidationResult) =
            if (results.none { it == Valid }) {
                Invalid.AnyOf(results.map { it as Invalid })
            } else {
                Valid
            }

        fun allOf(vararg results: ValidationResult) =
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

        fun oneOf(vararg results: ValidationResult) =
            if(results.filterIsInstance<Valid>().size != 1){
                Invalid.OneOf(results.toList())
            } else {
                Valid
            }

    }
}