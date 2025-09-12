package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantRequirementRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq

data class DataParticipantRequirementsExport(
    val participantRequirements: List<ParticipantRequirementExport>
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val participantRequirements = !Jooq.query { selectFrom(PARTICIPANT_REQUIREMENT).fetch() }.orDie()
                .map { list -> !list.traverse { it.toExport() } }

            val exportData = DataParticipantRequirementsExport(
                participantRequirements = participantRequirements
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS), bytes = json))
        }

        fun importData(data: DataParticipantRequirementsExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {
            try {
                val overlaps = !ParticipantRequirementRepo.getOverlapIds(data.participantRequirements.map { it.id }).orDie()
                
                val records = !data.participantRequirements
                    .filter { requirement -> !overlaps.any { it == requirement.id } }
                    .traverse { it.toRecord() }
                
                if (records.isNotEmpty()) {
                    !ParticipantRequirementRepo.create(records).orDie()
                }
            } catch (ex: Exception) {
                return@comprehension KIO.fail(WebDAVError.Unexpected)
            }
            unit
        }
    }
}