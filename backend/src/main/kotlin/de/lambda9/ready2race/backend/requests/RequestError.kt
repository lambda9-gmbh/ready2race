package de.lambda9.ready2race.backend.requests

import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ToApiError
import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.http.*

sealed interface RequestError: ToApiError {

    data class RequiredQueryParameterMissing(val key: String): RequestError
    data class PathParameterUnknown(val key: String): RequestError
    data class ParameterUnparsable(val key: String): RequestError
    data class BodyUnparsable(val example: Validatable): RequestError
    data class InvalidPagination(val result: StructuredValidationResult.Invalid): RequestError

    override fun respond(): ApiError = when (this) {
        is BodyUnparsable ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Request body is not parsable, probably missing required fields.",
                details = mapOf("example" to example)
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
        is PathParameterUnknown ->
            ApiError(
                status = HttpStatusCode.InternalServerError,
                message = "Requested path parameter $key does not exist"
            )
        is RequiredQueryParameterMissing ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Missing required query parameter $key"
            )
    }
}