package de.lambda9.ready2race.backend.requests

import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ToApiError
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.http.*

sealed interface RequestError : ToApiError {

    data class RequiredQueryParameterMissing(val key: String) : RequestError
    data class ParameterUnparsable(val key: String) : RequestError
    data class BodyUnparsable(val example: Validatable) : RequestError
    data class BodyValidationFailed(val reason: ValidationResult.Invalid) : RequestError
    data class InvalidPagination(val result: ValidationResult.Invalid) : RequestError
    data class TooManyRequests(val retryAfter: String?) : RequestError

    data class Other(val cause: Throwable) : RequestError

    override fun respond(): ApiError = when (this) {
        is BodyUnparsable ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Request body is not parsable, probably missing required fields.",
                details = mapOf("validExample" to example)
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

        is ParameterUnparsable ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Parameter $key could not be parsed"
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
                }"
            )

        is Other ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = cause.toString()
            )
    }
}