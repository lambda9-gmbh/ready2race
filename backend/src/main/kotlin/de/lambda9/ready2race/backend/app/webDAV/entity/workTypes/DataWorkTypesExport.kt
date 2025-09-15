package de.lambda9.ready2race.backend.app.webDAV.entity.workTypes

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.app.workType.control.WorkTypeRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WORK_TYPE
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataWorkTypesExport(
    val workTypes: List<WorkTypeExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val workTypes = !Jooq.query { selectFrom(WORK_TYPE).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataWorkTypesExport(
                workTypes = workTypes
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_WORK_TYPES), bytes = json))
        }

        fun importData(data: DataWorkTypesExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {

            val overlaps = !WorkTypeRepo.getOverlapIds(data.workTypes.map { it.id }).orDie()
            val records = !data.workTypes
                .filter { workType -> !overlaps.any { it == workType.id } }
                .traverse { it.toRecord() }

            if (records.isNotEmpty()) {
                !WorkTypeRepo.create(records).orDie()
            }

            unit
        }
    }
}