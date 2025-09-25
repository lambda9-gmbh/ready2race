package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataContactInformationExport(
    val contactInformation: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val contactInformation = !ContactInformationRepo.allAsJson().orDie()

            val json =
                !WebDAVExportService.serializeDataExportNew(record, mapOf("contactInformation" to contactInformation))

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_CONTACT_INFORMATION), bytes = json))
        }

        fun importData(data: DataContactInformationExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                !ContactInformationRepo.insertJsonData(data.contactInformation.toString()).orDie()

                unit
            }
    }
}