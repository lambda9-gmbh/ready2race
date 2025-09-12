package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.fee.control.FeeRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.FEE
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataFeesExport(
    val fees: List<FeeExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val fees = !Jooq.query { selectFrom(FEE).fetch() }.orDie()
                .map { list -> !list.traverse { it.toExport() } }

            val exportData = DataFeesExport(
                fees = fees
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_FEES), bytes = json))
        }

        fun importData(data: DataFeesExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {
            try {
                val overlaps = !FeeRepo.getOverlapIds(data.fees.map { it.id }).orDie()
                
                val records = !data.fees
                    .filter { fee -> !overlaps.any { it == fee.id } }
                    .traverse { it.toRecord() }
                
                if (records.isNotEmpty()) {
                    !FeeRepo.create(records).orDie()
                }
            } catch (ex: Exception) {
                return@comprehension KIO.fail(WebDAVError.Unexpected)
            }
            unit
        }
    }
}