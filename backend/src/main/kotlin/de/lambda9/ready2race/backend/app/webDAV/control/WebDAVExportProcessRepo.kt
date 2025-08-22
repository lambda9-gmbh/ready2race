package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportProcessRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_PROCESS
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertReturning

object WebDAVExportProcessRepo {

    fun create(record: WebdavExportProcessRecord) = WEBDAV_EXPORT_PROCESS.insertReturning(record) { ID }

}