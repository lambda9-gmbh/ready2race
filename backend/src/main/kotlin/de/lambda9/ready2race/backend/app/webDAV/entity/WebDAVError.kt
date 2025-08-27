package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.*

sealed interface WebDAVError {

    // scheduler-only

    sealed interface WebDAVInternError : WebDAVError


    data object NoFilesToExport: WebDAVInternError
    data class FileNotFound(val exportId: UUID, val referenceId: UUID?): WebDAVInternError
    data class CannotTransferFile(val exportId: UUID, val errorMsg: String): WebDAVInternError


    // API-only

    sealed class WebDAVExternError(val message: String) : WebDAVError, ServiceError {

        override fun respond(): ApiError = ApiError(
            status = when (this) {
                ConfigIncomplete -> HttpStatusCode.BadRequest
                is CannotMakeFolder /* TODO: other code? */, Unexpected -> HttpStatusCode.BadGateway
                ExportFolderAlreadyExists -> HttpStatusCode.Conflict
                ConfigUnparsable -> HttpStatusCode.InternalServerError
            },
            message = message,
        )
    }


    data object ExportFolderAlreadyExists : WebDAVExternError("Another export folder with the same name already exists on the WebDEV Server.")



    // both

    sealed class WebDavInternExternError(message: String) : WebDAVExternError(message), WebDAVInternError


    data object Unexpected : WebDavInternExternError("An unexpected error has occurred. This is most likely a network issue.")
    data object ConfigIncomplete : WebDavInternExternError("The config env file does not include the necessary WebDAV information.")
    data class CannotMakeFolder(val folderPath: String) : WebDavInternExternError("An error has occurred when creating new folder on the WebDAV Server. Folder path $folderPath")
    data object ConfigUnparsable : WebDavInternExternError("An unexpected error has occurred. The specified WebDAV adress could not be parsed.")
}
