package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.*

sealed interface WebDAVError {

    // scheduler-only

    sealed interface WebDAVInternError : WebDAVError

    data object NoFilesToExport : WebDAVInternError
    data class FileNotFound(val exportId: UUID, val referenceId: UUID?) : WebDAVInternError
    data class CannotTransferFile(val exportId: UUID, val errorMsg: String) : WebDAVInternError


    sealed interface WebDAVImportNextError : WebDAVError

    data class EmailExistingWithOtherId(val emails: List<String>) : WebDAVImportNextError
    data object TypeNotSupported : WebDAVImportNextError
    data class InsertFailed(val table: String, val errorMsg: String) : WebDAVImportNextError
    data class UnableToRetrieveFile(val importId: UUID, val errorMsg: String) : WebDAVImportNextError
    data class JsonToExportParsingFailed(val className: String, val errorMsg: String) : WebDAVImportNextError
    data class UnknownPrivilege(val privileges: List<Triple<String, String, String>>) : WebDAVImportNextError
    data class EntityAlreadyExists(val entityId: UUID) : WebDAVImportNextError

    // API-only

    sealed class WebDAVExternError(val message: String) : WebDAVError, ServiceError {

        override fun respond(): ApiError = ApiError(
            status = when (this) {
                ConfigIncomplete -> HttpStatusCode.BadRequest
                is CannotMakeFolder, Unexpected -> HttpStatusCode.BadGateway/* TODO: other code? */
                ExportFolderAlreadyExists -> HttpStatusCode.Conflict
                ConfigUnparsable,
                ManifestSerializationFailed -> HttpStatusCode.InternalServerError

                ManifestExportFailed,
                CannotListFolders -> HttpStatusCode.BadGateway

                ManifestNotFound -> HttpStatusCode.NotFound
                ManifestParsingFailed -> HttpStatusCode.InternalServerError
                is MissingDependency -> HttpStatusCode.BadRequest
                OnlyDataImportsAllowed -> HttpStatusCode.BadGateway
            },
            message = message,
        )
    }


    data object ExportFolderAlreadyExists :
        WebDAVExternError("Another export folder with the same name already exists on the WebDEV Server.")

    data object ManifestSerializationFailed :
        WebDAVExternError("Failed to serialize manifest.json file for database export.")

    data object ManifestExportFailed : WebDAVExternError("Failed to export manifest.json file to the WebDAV Server.")
    data object CannotListFolders : WebDAVExternError("Failed to list folders from the WebDAV Server.")
    data object ManifestNotFound : WebDAVExternError("Manifest.json file not found in the specified folder.")
    data object ManifestParsingFailed : WebDAVExternError("Failed to parse manifest.json file from the WebDAV Server.")
    data object OnlyDataImportsAllowed : WebDAVExternError("Only Data import types allowed.")
    data class MissingDependency(val requester: WebDAVExportType, val dependency: WebDAVExportType) :
        WebDAVExternError("Export type ${requester.name} requires ${dependency.name} to be selected as well.")


    // both

    sealed class WebDavInternExternError(message: String) : WebDAVExternError(message), WebDAVInternError,
        WebDAVImportNextError


    data object Unexpected :
        WebDavInternExternError("An unexpected error has occurred. This is most likely a network issue.")

    data object ConfigIncomplete :
        WebDavInternExternError("The config env file does not include the necessary WebDAV information.")

    data class CannotMakeFolder(val folderPath: String) :
        WebDavInternExternError("An error has occurred when creating a new folder on the WebDAV Server. Folder path $folderPath")

    data object ConfigUnparsable :
        WebDavInternExternError("An unexpected error has occurred. The specified WebDAV adress could not be parsed.")
}
