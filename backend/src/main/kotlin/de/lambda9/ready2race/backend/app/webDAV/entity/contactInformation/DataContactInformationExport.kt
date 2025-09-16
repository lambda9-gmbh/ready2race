package de.lambda9.ready2race.backend.app.webDAV.entity.contactInformation

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CONTACT_INFORMATION
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataContactInformationExport(
    val contactInformation: List<ContactInformationExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val contactInformation = !Jooq.query { selectFrom(CONTACT_INFORMATION).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataContactInformationExport(
                contactInformation = contactInformation
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_CONTACT_INFORMATION), bytes = json))
        }

        fun importData(data: DataContactInformationExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {
                val overlaps = !ContactInformationRepo.getOverlapIds(data.contactInformation.map { it.id }).orDie()
                val records = !data.contactInformation
                    .filter { !overlaps.contains(it.id) }
                    .traverse { it.toRecord() }

                if (records.isNotEmpty()) {
                    !ContactInformationRepo.create(records).orDie()
                }

                unit
            }
    }
}