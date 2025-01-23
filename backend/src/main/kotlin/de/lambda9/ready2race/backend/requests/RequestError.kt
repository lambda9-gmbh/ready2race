package de.lambda9.ready2race.backend.requests

import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ToApiError
import io.ktor.http.*

sealed interface RequestError: ToApiError {

    data class MissingRequiredParameter(val key: String): RequestError
    data class ParameterUnparsable(val key: String): RequestError
    data object InvalidPagination: RequestError

    override fun respond(): ApiError =
        ApiError(
            status = HttpStatusCode.BadRequest,
            message = when (this) {
                is MissingRequiredParameter -> "Missing required query parameter $key"
                is ParameterUnparsable -> "Query parameter $key could not be parsed"
                InvalidPagination -> "Invalid pagination parameters (limit must be bigger than '0', offset must not be negative)"
            }
        )
}