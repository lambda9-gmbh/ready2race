package de.lambda9.ready2race.backend.validation

import com.fasterxml.jackson.annotation.JsonValue
import de.lambda9.ready2race.backend.serialization.jsonMapper
import io.ktor.server.plugins.requestvalidation.*

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