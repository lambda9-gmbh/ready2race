package de.lambda9.ready2race.backend.app.webDAV.entity.matchResultsImportConfigs

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.matchResultImportConfig.control.MatchResultImportConfigRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.MATCH_RESULT_IMPORT_CONFIG
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataMatchResultImportConfigsExport(
    val matchResultImportConfigs: List<MatchResultImportConfigExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val matchResultImportConfigs = !Jooq.query { selectFrom(MATCH_RESULT_IMPORT_CONFIG).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataMatchResultImportConfigsExport(
                matchResultImportConfigs = matchResultImportConfigs
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(
                File(
                    name = getWebDavDataJsonFileName(WebDAVExportType.DB_MATCH_RESULT_IMPORT_CONFIGS),
                    bytes = json
                )
            )
        }

        fun importData(data: DataMatchResultImportConfigsExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {
                val overlaps =
                    !MatchResultImportConfigRepo.getOverlapIds(data.matchResultImportConfigs.map { it.id }).orDie()
                val records = !data.matchResultImportConfigs
                    .filter { !overlaps.contains(it.id) }
                    .traverse { it.toRecord() }

                if (records.isNotEmpty()) {
                    !MatchResultImportConfigRepo.create(records).orDie()
                }

                unit
            }
    }
}