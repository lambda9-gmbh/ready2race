package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class GapDocumentTemplateError : ServiceError {
    NotFound;

    override fun respond(): ApiError = when(this) {
        NotFound ->
            ApiError(
                status = HttpStatusCode.NotFound,
                message = "Template not found"
            )
    }
}