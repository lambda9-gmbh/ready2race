package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import java.time.LocalDateTime
import java.util.*

data class WebDAVExportStatusDto(
    val processId: UUID,
    val exportFolderName: String,
    val exportInitializedAt: LocalDateTime,
    val exportInitializedBy: AppUserNameDto?,
    val dataExportEvents: List<String?>,
    val fileExportEvents: List<FileExportEventStatusDto>,
    val filesExported: Int,
    val totalFilesToExport: Int,
    val filesWithError: Int,
    val dataExported: Int,
    val totalDataToExport: Int,
    val dataWithError: Int
)