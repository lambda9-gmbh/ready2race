package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class GapDocumentTemplateError : ServiceError {
    NotFound,
    InvalidFont,
    PlaceholderPageNotSupported,
    PlaceholderTypeNotSupported,
    TemplateTypeMismatch;

    override fun respond(): ApiError = when(this) {
        NotFound ->
            ApiError(
                status = HttpStatusCode.NotFound,
                message = "Template not found"
            )

        TemplateTypeMismatch ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Template type does not match the document type it is assigned to"
            )

        InvalidFont ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Font file could not be read"
            )

        PlaceholderPageNotSupported ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Award certificates have a single page, placeholders must be on page 1"
            )

        PlaceholderTypeNotSupported ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Placeholder type is not supported for the chosen document type"
            )
    }
}
