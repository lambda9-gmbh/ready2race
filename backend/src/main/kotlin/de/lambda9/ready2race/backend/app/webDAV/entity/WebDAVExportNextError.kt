package de.lambda9.ready2race.backend.app.webDAV.entity

import java.util.UUID

sealed interface WebDAVExportNextError {
    data object ConfigIncomplete: WebDAVExportNextError
    data object NoFilesToExport: WebDAVExportNextError
    data class ThirdPartyError(val exportId: UUID, val cause: Throwable): WebDAVExportNextError
    data class FileNotFound(val exportId: UUID, val referenceId: UUID?): WebDAVExportNextError
}