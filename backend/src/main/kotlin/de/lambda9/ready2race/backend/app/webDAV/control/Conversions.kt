package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.toDto
import de.lambda9.ready2race.backend.app.webDAV.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.records.*
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
    dataExportEvents: List<String?>,
    fileExportEvents: List<FileExportEventStatusDto>,
    filesExported: Int,
    filesWithError: Int,
    dataExported: Int,
    dataWithError: Int,
): App<Nothing, WebDAVExportStatusDto> = KIO.comprehension {
    val createdByDto = createdBy?.let { !it.toDto() }

    KIO.ok(
        WebDAVExportStatusDto(
            processId = id!!,
            exportFolderName = name!!,
            exportInitializedAt = createdAt!!,
            exportInitializedBy = createdByDto,
            dataExportEvents = dataExportEvents,
            fileExportEvents = fileExportEvents,
            filesExported = filesExported,
            totalFilesToExport = fileExports!!.size,
            filesWithError = filesWithError,
            dataExported = dataExported,
            totalDataToExport = dataExports!!.filterNotNull().size,
            dataWithError = dataWithError
        )
    )
}

fun WebdavImportProcessStatusRecord.toDto(
    imported: Int,
    importsWithError: Int,
): App<Nothing, WebDAVImportStatusDto> = KIO.comprehension {
    val createdByDto = createdBy?.let { !it.toDto() }

    KIO.ok(
        WebDAVImportStatusDto(
            processId = id!!,
            importFolderName = importFolderName!!,
            importInitializedAt = createdAt!!,
            importInitializedBy = createdByDto,
            dataImported = imported,
            totalDataToImport = imports!!.filterNotNull().size,
            dataWithError = importsWithError
        )
    )
}