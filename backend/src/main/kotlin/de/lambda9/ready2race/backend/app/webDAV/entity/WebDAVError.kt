package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class WebDAVError : ServiceError {
    ConfigIncomplete,
    ExportThirdPartyError;

    override fun respond(): ApiError = when (this) {
        ConfigIncomplete -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The config env file does not include the necessary WebDAV information."
        )
        ExportThirdPartyError -> ApiError(
            status = HttpStatusCode.BadGateway,
            message = "An error has occurred when exporting the files to the WebDAV Server."
        )
    }
}