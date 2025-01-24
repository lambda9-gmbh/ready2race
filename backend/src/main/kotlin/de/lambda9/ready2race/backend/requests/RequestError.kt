package de.lambda9.ready2race.backend.requests

import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ToApiError
import io.ktor.http.*

sealed interface RequestError: ToApiError {

    data class RequiredQueryParameterMissing(val key: String): RequestError
    data class PathParameterUnknown(val key: String): RequestError
    data class ParameterUnparsable(val key: String): RequestError
    data object InvalidPagination: RequestError

    override fun respond(): ApiError =
        ApiError(
            status = if (this is PathParameterUnknown) HttpStatusCode.InternalServerError else HttpStatusCode.BadRequest,
            message = when (this) {
                is PathParameterUnknown -> "Requested path parameter $key does not exist"
                is RequiredQueryParameterMissing -> "Missing required query parameter $key"
                is ParameterUnparsable -> "Query parameter $key could not be parsed"
                InvalidPagination -> "Invalid pagination parameters (limit must be bigger than '0', offset must not be negative)"
            }
        )
}