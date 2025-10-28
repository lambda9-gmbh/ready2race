package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportProcessRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_IMPORT_PROCESS
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_IMPORT_PROCESS_STATUS
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import org.jooq.impl.DSL

object WebDAVImportProcessRepo {

    fun create(record: WebdavImportProcessRecord) = WEBDAV_IMPORT_PROCESS.insertReturning(record) { ID }

    fun all() = WEBDAV_IMPORT_PROCESS_STATUS.select { DSL.trueCondition() }
}