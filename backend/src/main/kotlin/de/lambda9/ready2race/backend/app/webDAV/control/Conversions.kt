package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportProcessRecord
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