package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportProcessRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_PROCESS
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_PROCESS_STATUS
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import org.jooq.impl.DSL

object WebDAVExportProcessRepo {

    fun create(record: WebdavExportProcessRecord) = WEBDAV_EXPORT_PROCESS.insertReturning(record) { ID }

    fun all() = WEBDAV_EXPORT_PROCESS_STATUS.select { DSL.trueCondition() }
}