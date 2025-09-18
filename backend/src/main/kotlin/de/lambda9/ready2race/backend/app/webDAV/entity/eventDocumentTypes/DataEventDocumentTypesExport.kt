package de.lambda9.ready2race.backend.app.webDAV.entity.eventDocumentTypes

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventDocumentType.control.EventDocumentTypeRepo
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

data class DataEventDocumentTypesExport(
    val eventDocumentTypes: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val eventDocumentTypes = !EventDocumentTypeRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(record, mapOf("eventDocumentTypes" to eventDocumentTypes))

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_EVENT_DOCUMENT_TYPES), bytes = json))
        }

        fun importData(data: DataEventDocumentTypesExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {

            !EventDocumentTypeRepo.insertJsonData(data.eventDocumentTypes.toString()).orDie()

            unit
        }
    }
}