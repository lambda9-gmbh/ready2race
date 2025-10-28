package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.startListConfig.control.StartListConfigRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataStartlistExportConfigsExport(
    val startlistExportConfigs: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val startlistExportConfigs = !StartListConfigRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(
                record,
                mapOf("startlistExportConfigs" to startlistExportConfigs)
            )

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_STARTLIST_EXPORT_CONFIGS), bytes = json))
        }

        fun importData(data: DataStartlistExportConfigsExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                !StartListConfigRepo.insertJsonData(data.startlistExportConfigs.toString()).orDie()

                unit
            }
    }
}