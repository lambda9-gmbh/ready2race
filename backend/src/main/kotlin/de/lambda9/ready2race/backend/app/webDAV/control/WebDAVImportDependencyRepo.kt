package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportDependencyRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_IMPORT_DEPENDENCY
import de.lambda9.ready2race.backend.database.insert
import java.util.*

object WebDAVImportDependencyRepo {

    fun create(records: List<WebdavImportDependencyRecord>) = WEBDAV_IMPORT_DEPENDENCY.insert(records)

    fun removeByImportDataId(importDataId: UUID) =
        WEBDAV_IMPORT_DEPENDENCY.delete { WEBDAV_IMPORT_DATA.eq(importDataId) }
}