package de.lambda9.ready2race.backend.app.webDAV.entity.eventDocumentTypes

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventDocumentType.control.EventDocumentTypeRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_TYPE
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataEventDocumentTypesExport(
    val eventDocumentTypes: List<EventDocumentTypeExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val eventDocumentTypes = !Jooq.query { selectFrom(EVENT_DOCUMENT_TYPE).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataEventDocumentTypesExport(
                eventDocumentTypes = eventDocumentTypes
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_EVENT_DOCUMENT_TYPES), bytes = json))
        }

        fun importData(data: DataEventDocumentTypesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {
                try {
                    val overlaps = !EventDocumentTypeRepo.getOverlapIds(data.eventDocumentTypes.map { it.id }).orDie()

                    val records = !data.eventDocumentTypes
                        .filter { docType -> !overlaps.any { it == docType.id } }
                        .traverse { it.toRecord() }

                    if (records.isNotEmpty()) {
                        !EventDocumentTypeRepo.create(records).orDie()
                    }
                } catch (ex: Exception) {
                    return@comprehension KIO.fail(WebDAVError.Unexpected)
                }
                unit
            }
    }
}