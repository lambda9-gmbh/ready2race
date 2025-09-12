package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import java.time.LocalDateTime
import java.util.*

data class WebDAVExportStatusDto (
    val processId: UUID,
    val exportFolderName: String,
    val exportInitializedAt: LocalDateTime,
    val exportInitializedBy: AppUserNameDto?,
    val events: List<String>,
    val exportTypes: List<WebDAVExportType>,
    val filesExported: Int,
    val totalFilesToExport: Int,
    val filesWithError: Int
)