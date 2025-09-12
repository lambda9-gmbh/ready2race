package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_DATA
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_DATA_DEPENDENCY_VIEW
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_EXPORT_DEPENDENCY
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectAny
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object WebDAVExportDataRepo {

    fun create(records: List<WebdavExportDataRecord>) = WEBDAV_EXPORT_DATA.insert(records)

    fun createOne(record: WebdavExportDataRecord) = WEBDAV_EXPORT_DATA.insert(record)

    fun getNextExport() = WEBDAV_EXPORT_DATA_DEPENDENCY_VIEW.selectAny({ DATA }) {
        EXPORTED_AT.isNull
            .and(ERROR_AT.isNull)
            .and(ALL_DEPENDENCIES_EXPORTED.isTrue)
    }

    fun update(record: WebdavExportDataRecord, f: WebdavExportDataRecord.() -> Unit) =
        WEBDAV_EXPORT_DATA.update(record, f)

    fun updateByDependingOnId(dependingOn: UUID, f: WebdavExportDataRecord.() -> Unit) = Jooq.query {
        val records = fetch(
            selectFrom(WEBDAV_EXPORT_DATA)
                .where(
                    WEBDAV_EXPORT_DATA.ID.`in`(
                        select(WEBDAV_EXPORT_DEPENDENCY.WEBDAV_EXPORT_DATA)
                            .from(WEBDAV_EXPORT_DEPENDENCY)
                            .where(WEBDAV_EXPORT_DEPENDENCY.DEPENDING_ON.eq(dependingOn))
                    )
                )
        )

        if (records.isNotEmpty) {
            records.forEach { it.f() }
            batchUpdate(records).execute()
        }
        
        records
    }
}