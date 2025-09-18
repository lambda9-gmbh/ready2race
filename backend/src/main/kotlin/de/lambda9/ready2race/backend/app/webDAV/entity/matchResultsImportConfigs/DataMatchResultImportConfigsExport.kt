package de.lambda9.ready2race.backend.app.webDAV.entity.matchResultsImportConfigs

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.matchResultImportConfig.control.MatchResultImportConfigRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataMatchResultImportConfigsExport(
    val matchResultImportConfigs: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val matchResultImportConfigs = !MatchResultImportConfigRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(record, mapOf("matchResultImportConfigs" to matchResultImportConfigs))

            KIO.ok(
                File(
                    name = getWebDavDataJsonFileName(WebDAVExportType.DB_MATCH_RESULT_IMPORT_CONFIGS),
                    bytes = json
                )
            )
        }

        fun importData(data: DataMatchResultImportConfigsExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {

            !MatchResultImportConfigRepo.insertJsonData(data.matchResultImportConfigs.toString()).orDie()

            unit
        }
    }
}