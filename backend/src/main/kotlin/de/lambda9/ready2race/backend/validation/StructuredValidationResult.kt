package de.lambda9.ready2race.backend.validation

import com.fasterxml.jackson.annotation.JsonValue
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.serialization.jsonMapper
import io.ktor.http.*
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

        data class AllOf(val allOf: List<Invalid>) : Invalid
        data class AnyOf(val anyOf: List<Invalid>) : Invalid
    }

    fun <A> fold(
        onValid: () -> A,
        onInvalid: (Invalid) -> A,
    ): A = when (this) {
        is Invalid -> onInvalid(this)
        Valid -> onValid()
    }

    fun toValidationResult(): ValidationResult = when (this) {
        is Invalid -> ValidationResult.Invalid(
            jsonMapper.writeValueAsString(
                ApiError(
                    status = HttpStatusCode.BadRequest,
                    message = "Validation of request payload failed.",
                    details = mapOf("errors" to this)
                )
            )
        )
        Valid -> ValidationResult.Valid
    }

    companion object {
        fun anyOf(vararg results: StructuredValidationResult) =
            if (results.none { it == Valid }) {
                Invalid.AnyOf(results.filterIsInstance<Invalid>())
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