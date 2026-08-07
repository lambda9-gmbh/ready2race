package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

enum class GapDocumentTemplateError : ServiceError {
    NotFound,
    InvalidFont,
    InvalidPdf,
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
                message = "Template type does not match the document type it is assigned to",
                errorCode = ErrorCode.DOCUMENT_TEMPLATE_TYPE_MISMATCH,
            )

        InvalidFont ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Font file could not be read",
                errorCode = ErrorCode.DOCUMENT_TEMPLATE_INVALID_FONT,
            )

        InvalidPdf ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Template file could not be read as PDF",
                errorCode = ErrorCode.DOCUMENT_TEMPLATE_INVALID_PDF,
            )

        PlaceholderPageNotSupported ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Award certificates have a single page, placeholders must be on page 1",
                errorCode = ErrorCode.DOCUMENT_TEMPLATE_PLACEHOLDER_PAGE_NOT_SUPPORTED,
            )

        PlaceholderTypeNotSupported ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Placeholder type is not supported for the chosen document type",
                errorCode = ErrorCode.DOCUMENT_TEMPLATE_PLACEHOLDER_TYPE_NOT_SUPPORTED,
            )
    }
}
