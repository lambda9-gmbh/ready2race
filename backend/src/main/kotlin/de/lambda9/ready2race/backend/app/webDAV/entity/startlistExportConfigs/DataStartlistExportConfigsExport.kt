package de.lambda9.ready2race.backend.app.webDAV.entity.startlistExportConfigs

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.startListConfig.control.StartListConfigRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.STARTLIST_EXPORT_CONFIG
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataStartlistExportConfigsExport(
    val startlistExportConfigs: List<StartlistExportConfigExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val startlistExportConfigs = !Jooq.query { selectFrom(STARTLIST_EXPORT_CONFIG).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataStartlistExportConfigsExport(
                startlistExportConfigs = startlistExportConfigs
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_STARTLIST_EXPORT_CONFIGS), bytes = json))
        }

        fun importData(data: DataStartlistExportConfigsExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {
                try {
                    val overlaps = !StartListConfigRepo.getOverlapIds(data.startlistExportConfigs.map { it.id }).orDie()

                    val records = !data.startlistExportConfigs
                        .filter { config -> !overlaps.any { it == config.id } }
                        .traverse { it.toRecord() }

                    if (records.isNotEmpty()) {
                        !StartListConfigRepo.create(records).orDie()
                    }
                } catch (ex: Exception) {
                    return@comprehension KIO.fail(WebDAVError.Unexpected)
                }
                unit
            }
    }
}