package de.lambda9.ready2race.backend.calls.requests

import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.http.*
import kotlin.reflect.KClass

sealed interface RequestError : ToApiError {

    data class PathParameterMissing(val key: String) : RequestError
    data class RequiredQueryParameterMissing(val key: String) : RequestError
    data class ParameterUnparsable<A : Any>(val key: String, val input: String, val kClass: KClass<A>) : RequestError
    data class BodyMissing(val example: Validatable) : RequestError
    data class BodyUnparsable(val example: Validatable) : RequestError
    data class BodyValidationFailed(val reason: ValidationResult.Invalid) : RequestError
    data class InvalidPagination(val result: ValidationResult.Invalid) : RequestError
    data class TooManyRequests(val retryAfter: String?) : RequestError

    sealed interface File : RequestError {
        data object Missing : File
        data object Multiple : File
        data object UnsupportedType : File
    }

    data class Other(val cause: Throwable) : RequestError

    override fun respond(): ApiError = when (this) {
        is PathParameterMissing ->
            ApiError(
                status = HttpStatusCode.InternalServerError,
                message = "Could not correctly resolve given path parameter '$key'. Please contact support.",
            )
        is BodyMissing ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Required request body is missing",
                details = mapOf("exampleBody" to example)
            )

        is BodyUnparsable ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Request body is not parsable, probably missing required fields.",
                details = mapOf("exampleBody" to example)
            )

        is BodyValidationFailed ->
            ApiError(
                status = HttpStatusCode.UnprocessableEntity,
                message = "Validation of request payload failed.",
                details = mapOf("reason" to reason)
            )

        is InvalidPagination ->
            ApiError(
                status = HttpStatusCode.UnprocessableEntity,
                message = "Invalid pagination parameters",
                details = mapOf("errors" to result)
            )

        is ParameterUnparsable<*> ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Parameter $key could not be parsed",
                details = mapOf(
                    "input" to input,
                    "expectedType"  to kClass.toString()
                ),
            )

        is RequiredQueryParameterMissing ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Missing required query parameter $key"
            )

        is TooManyRequests ->
            ApiError(
                status = HttpStatusCode.TooManyRequests,
                message = "Too many requests. Try again ${
                    if (retryAfter !== null) {
                        "in $retryAfter seconds"
                    } else {
                        "later"
                    }
                }",
                details = mapOf("retryAfter" to retryAfter)
            )

        File.Missing ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Missing required file upload"
            )

        File.Multiple ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Expected one file, got multiple"
            )

        File.UnsupportedType ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Unsupported file type"
            )

        is Other ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = cause.toString()
            )
    }
}