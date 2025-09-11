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
    events: List<String>,
    exportTypes: List<WebDAVExportType>,
    filesExported: Int,
    filesWithError: Int,
): App<Nothing, WebDAVExportStatusDto> = KIO.comprehension {
    val createdByDto = createdBy?.let { !it.toDto() }

    KIO.ok(
        WebDAVExportStatusDto(
            processId = id!!,
            exportFolderName = name!!,
            exportInitializedAt = createdAt!!,
            exportInitializedBy = createdByDto,
            events = events,
            exportTypes = exportTypes,
            filesExported = filesExported,
            totalFilesToExport = fileExports!!.size,
            filesWithError = filesWithError
        )
    )
}


// EXPORT CONVERSIONS

fun AppUserRecord.toExport(): App<Nothing, AppUserExport> = KIO.ok(
    AppUserExport(
        id = id,
        email = email,
        password = password,
        firstname = firstname,
        lastname = lastname,
        language = language,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
        club = club
    )
)

fun RoleRecord.toExport(): App<Nothing, RoleExport> = KIO.ok(
    RoleExport(
        id = id,
        name = name,
        description = description,
        static = static,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun RoleHasPrivilegeRecord.toExport(): App<Nothing, RoleHasPrivilegeExport> = KIO.ok(
    RoleHasPrivilegeExport(
        role = role,
        privilege = privilege
    )
)

fun AppUserHasRoleRecord.toExport(): App<Nothing, AppUserHasRoleExport> = KIO.ok(
    AppUserHasRoleExport(
        appUser = appUser,
        role = role
    )
)