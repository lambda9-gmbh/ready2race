package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportRequest
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportStatusDto
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportProcessRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportProcessStatusRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun WebDAVExportRequest.toRecord(
    userId: UUID,
): App<Nothing, WebdavExportProcessRecord> = KIO.ok(
    WebdavExportProcessRecord(
        id = UUID.randomUUID(),
        name = name,
        createdAt = LocalDateTime.now(),
        createdBy = userId,
    )
)

fun WebdavExportProcessStatusRecord.toDto(
    events: List<String>,
    exportTypes: List<WebDAVExportType>,
    filesExported: Int,
    filesWithError: Int,
): App<Nothing, WebDAVExportStatusDto> = KIO.ok(
    WebDAVExportStatusDto(
        processId = id!!,
        exportFolderName = name!!,
        exportInitializedAt = createdAt!!,
        exportInitializedBy = AppUserNameDto(
            id = createdById!!,
            firstname = createdByFirstname!!,
            lastname = createdByLastname!!,
        ),
        events = events,
        exportTypes = exportTypes,
        filesExported = filesExported,
        totalFilesToExport = fileExports!!.size,
        filesWithError = filesWithError
    )
)