package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class WebDAVError : ServiceError {
    ConfigIncomplete,
    ThirdPartyError,
    ExportFolderAlreadyExists,
    ConfigUnparsable,
    Unexpected;

    override fun respond(): ApiError = when (this) {
        ConfigIncomplete -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The config env file does not include the necessary WebDAV information."
        )
        ThirdPartyError -> ApiError(
            status = HttpStatusCode.BadGateway,
            message = "An error has occurred when exporting the files to the WebDAV Server."
        )
        ExportFolderAlreadyExists -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Another export folder with the same name already exists on the WebDEV Server."
        )
        ConfigUnparsable -> ApiError(
            status = HttpStatusCode.InternalServerError,
            message = "An unexpected error has occurred. The specified WebDAV adress could not be parsed."
        )
        Unexpected -> ApiError(
            status = HttpStatusCode.InternalServerError,
            message = "An unexpected error has occurred. This is most likely a network issue."
        )
    }
}