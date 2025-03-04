package de.lambda9.ready2race.backend.app.eventDocumentType.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

enum class EventDocumentTypeError : ServiceError {
    NotFound;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            HttpStatusCode.NotFound,
            message = "Document type not found"
        )
    }
}