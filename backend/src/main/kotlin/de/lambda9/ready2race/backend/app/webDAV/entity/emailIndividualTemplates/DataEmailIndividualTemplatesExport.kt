package de.lambda9.ready2race.backend.app.webDAV.entity.emailIndividualTemplates

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.email.control.EmailIndividualTemplateRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_INDIVIDUAL_TEMPLATE
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataEmailIndividualTemplatesExport(
    val emailIndividualTemplates: List<EmailIndividualTemplateExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val emailIndividualTemplates = !Jooq.query { selectFrom(EMAIL_INDIVIDUAL_TEMPLATE).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataEmailIndividualTemplatesExport(
                emailIndividualTemplates = emailIndividualTemplates
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_EMAIL_INDIVIDUAL_TEMPLATES), bytes = json))
        }

        fun importData(data: DataEmailIndividualTemplatesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {
                try {
                    val keyLanguagePairs = data.emailIndividualTemplates.map { it.key to it.language }
                    val overlaps = !EmailIndividualTemplateRepo.getOverlapKeyLanguagePairs(keyLanguagePairs).orDie()

                    val records = !data.emailIndividualTemplates
                        .filter { template -> !overlaps.any { it.first == template.key && it.second == template.language } }
                        .traverse { it.toRecord() }

                    if (records.isNotEmpty()) {
                        !EmailIndividualTemplateRepo.create(records).orDie()
                    }
                } catch (ex: Exception) {
                    return@comprehension KIO.fail(WebDAVError.Unexpected)
                }
                unit
            }
    }
}