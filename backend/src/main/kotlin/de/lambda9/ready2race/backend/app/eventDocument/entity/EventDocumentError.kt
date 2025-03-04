package de.lambda9.ready2race.backend.app.eventDocument.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

enum class EventDocumentError : ServiceError {
    NotFound;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Document not found"
        )
    }
}