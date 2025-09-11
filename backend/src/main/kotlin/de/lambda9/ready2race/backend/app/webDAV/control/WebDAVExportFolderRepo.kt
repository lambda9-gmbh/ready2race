package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportFolderRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_FOLDER
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_FOLDER_VIEW
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectAny
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object WebDAVExportFolderRepo {

    fun create(records: List<WebdavExportFolderRecord>) = WEBDAV_EXPORT_FOLDER.insert(records)

    fun getNextFolder() =
        WEBDAV_EXPORT_FOLDER_VIEW.selectAny {
            (PAREND_FOLDER_ID.isNull.or(PARENT_FOLDER_DONE_AT.isNotNull)).and(ERROR_AT.isNull).and(DONE_AT.isNull)
        }

    fun update(id: UUID, f: WebdavExportFolderRecord.() -> Unit) =
        WEBDAV_EXPORT_FOLDER.update(f) { ID.eq(id) }

    fun getByParentFolderId(parentFolderId: UUID) = WEBDAV_EXPORT_FOLDER.select { PARENT_FOLDER.eq(parentFolderId) }

    fun updateMany(records: List<WebdavExportFolderRecord>, f: WebdavExportFolderRecord.() -> Unit) = Jooq.query {
        records.forEach { it.f() }
        batchUpdate(records)
            .execute()
    }
}