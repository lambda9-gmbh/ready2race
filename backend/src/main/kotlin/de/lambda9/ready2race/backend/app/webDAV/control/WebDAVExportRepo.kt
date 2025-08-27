package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectAny
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.backend.database.updateMany
import java.util.*

object WebDAVExportRepo {

    fun create(records: List<WebdavExportRecord>) = WEBDAV_EXPORT.insert(records)

    fun getNextExport() = WEBDAV_EXPORT.selectAny { EXPORTED_AT.isNull.and(ERROR_AT.isNull) }

    fun update(record: WebdavExportRecord, f: WebdavExportRecord.() -> Unit) = WEBDAV_EXPORT.update(record, f)

    fun updateManyByParentFolderId(parentFolderId: UUID, f: WebdavExportRecord.() -> Unit) =
        WEBDAV_EXPORT.updateMany(f) { PARENT_FOLDER.eq(parentFolderId) }
}