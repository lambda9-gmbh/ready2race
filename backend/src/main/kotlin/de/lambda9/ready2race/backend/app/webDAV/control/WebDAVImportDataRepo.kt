package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_IMPORT_DATA
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_IMPORT_DATA_DEPENDENCY_VIEW
import de.lambda9.ready2race.backend.database.generated.tables.references.WEBDAV_IMPORT_DEPENDENCY
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectAny
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object WebDAVImportDataRepo {

    fun create(records: List<WebdavImportDataRecord>) = WEBDAV_IMPORT_DATA.insert(records)

    fun getNextImport() = WEBDAV_IMPORT_DATA_DEPENDENCY_VIEW.selectAny({ DATA }) {
        IMPORTED_AT.isNull
            .and(ERROR_AT.isNull)
            .and(ALL_DEPENDENCIES_IMPORTED.isTrue)
    }

    fun update(record: WebdavImportDataRecord, f: WebdavImportDataRecord.() -> Unit) =
        WEBDAV_IMPORT_DATA.update(record, f)

    fun updateByDependingOnId(dependingOn: UUID, f: WebdavImportDataRecord.() -> Unit) = Jooq.query {
        val records = fetch(
            selectFrom(WEBDAV_IMPORT_DATA)
                .where(
                    WEBDAV_IMPORT_DATA.ID.`in`(
                        select(WEBDAV_IMPORT_DEPENDENCY.WEBDAV_IMPORT_DATA)
                            .from(WEBDAV_IMPORT_DEPENDENCY)
                            .where(WEBDAV_IMPORT_DEPENDENCY.DEPENDING_ON.eq(dependingOn))
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