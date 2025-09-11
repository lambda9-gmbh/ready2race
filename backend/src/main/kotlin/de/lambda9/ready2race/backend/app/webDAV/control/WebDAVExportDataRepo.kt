package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_DATA
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectAny
import de.lambda9.ready2race.backend.database.update

object WebDAVExportDataRepo {

    fun create(records: List<WebdavExportDataRecord>) = WEBDAV_EXPORT_DATA.insert(records)

    fun createOne(record: WebdavExportDataRecord) = WEBDAV_EXPORT_DATA.insert(record)

    fun getNextExport() = WEBDAV_EXPORT_DATA.selectAny { EXPORTED_AT.isNull.and(ERROR_AT.isNull) }

    fun update(record: WebdavExportDataRecord, f: WebdavExportDataRecord.() -> Unit) = WEBDAV_EXPORT_DATA.update(record, f)
}