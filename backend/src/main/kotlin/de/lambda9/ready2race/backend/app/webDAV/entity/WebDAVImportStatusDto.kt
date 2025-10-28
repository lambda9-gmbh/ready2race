package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import java.time.LocalDateTime
import java.util.*

data class WebDAVImportStatusDto(
    val processId: UUID,
    val importFolderName: String,
    val importInitializedAt: LocalDateTime,
    val importInitializedBy: AppUserNameDto?,
    val dataImported: Int,
    val totalDataToImport: Int,
    val dataWithError: Int
)