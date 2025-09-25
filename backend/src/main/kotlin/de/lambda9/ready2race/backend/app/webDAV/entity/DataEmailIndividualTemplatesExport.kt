package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.email.control.EmailIndividualTemplateRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataEmailIndividualTemplatesExport(
    val emailIndividualTemplates: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val emailIndividualTemplates = !EmailIndividualTemplateRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(
                record,
                mapOf("emailIndividualTemplates" to emailIndividualTemplates)
            )

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_EMAIL_INDIVIDUAL_TEMPLATES), bytes = json))
        }

        fun importData(data: DataEmailIndividualTemplatesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                !EmailIndividualTemplateRepo.insertJsonData(data.emailIndividualTemplates.toString()).orDie()

                unit
            }
    }
}