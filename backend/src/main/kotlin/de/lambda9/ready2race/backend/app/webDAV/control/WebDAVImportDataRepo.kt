package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_IMPORT_DATA
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectAny
import de.lambda9.ready2race.backend.database.update

object WebDAVImportDataRepo {

    fun create(records: List<WebdavImportDataRecord>) = WEBDAV_IMPORT_DATA.insert(records)

    fun getNextImport() = WEBDAV_IMPORT_DATA.selectAny { IMPORTED_AT.isNull.and(ERROR_AT.isNull) }

    fun update(record: WebdavImportDataRecord, f: WebdavImportDataRecord.() -> Unit) = WEBDAV_IMPORT_DATA.update(record, f)
}