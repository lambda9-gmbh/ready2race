package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantRequirementRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataParticipantRequirementsExport(
    val participantRequirements: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val participantRequirements = !ParticipantRequirementRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(
                record,
                mapOf("participantRequirements" to participantRequirements)
            )

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS), bytes = json))
        }

        fun importData(data: DataParticipantRequirementsExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                !ParticipantRequirementRepo.insertJsonData(data.participantRequirements.toString()).orDie()

                unit
            }
    }
}