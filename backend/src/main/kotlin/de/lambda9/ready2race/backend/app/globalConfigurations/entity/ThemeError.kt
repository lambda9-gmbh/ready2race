package de.lambda9.ready2race.backend.app.globalConfigurations.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class ThemeError : ServiceError {
    ThemeFileNotFound,
    ThemeFileMalformed,
    FontFileInvalid,
    FontFileTooLarge;

    override fun respond(): ApiError = when (this) {
        ThemeFileNotFound -> ApiError(
            HttpStatusCode.NotFound,
            message = "Theme configuration file not found"
        )
        ThemeFileMalformed -> ApiError(
            HttpStatusCode.InternalServerError,
            message = "Theme configuration file is malformed"
        )
        FontFileInvalid -> ApiError(
            HttpStatusCode.BadRequest,
            message = "Font file must be .woff or .woff2 format"
        )
        FontFileTooLarge -> ApiError(
            HttpStatusCode.BadRequest,
            message = "Font file size must not exceed 5MB"
        )
    }
}
