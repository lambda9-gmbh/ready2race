package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDependencyRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_DEPENDENCY
import de.lambda9.ready2race.backend.database.insert
import java.util.UUID

object WebDAVExportDependencyRepo {

    fun create(records: List<WebdavExportDependencyRecord>) = WEBDAV_EXPORT_DEPENDENCY.insert(records)

    fun removeByExportDataId(exportDataId: UUID) =
        WEBDAV_EXPORT_DEPENDENCY.delete { WEBDAV_EXPORT_DATA.eq(exportDataId) }
}