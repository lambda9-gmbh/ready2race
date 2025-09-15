package de.lambda9.ready2race.backend.app.webDAV.entity.namedParticipants

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataNamedParticipantsExport(
    val namedParticipants: List<NamedParticipantExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val namedParticipants = !Jooq.query { selectFrom(NAMED_PARTICIPANT).fetch() }.orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataNamedParticipantsExport(
                namedParticipants = namedParticipants
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_NAMED_PARTICIPANTS), bytes = json))
        }

        fun importData(data: DataNamedParticipantsExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {
                try {
                    val overlaps = !NamedParticipantRepo.getOverlapIds(data.namedParticipants.map { it.id }).orDie()

                    val records = !data.namedParticipants
                        .filter { participant -> !overlaps.any { it == participant.id } }
                        .traverse { it.toRecord() }

                    if (records.isNotEmpty()) {
                        !NamedParticipantRepo.create(records).orDie()
                    }
                } catch (ex: Exception) {
                    return@comprehension KIO.fail(WebDAVError.Unexpected)
                }
                unit
            }
    }
}