package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataParticipantsExport(
    val participants: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val participants = !ParticipantRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(record, mapOf("participants" to participants))

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_PARTICIPANTS), bytes = json))
        }

        fun importData(data: DataParticipantsExport): App<Nothing, Unit> = KIO.comprehension {

            !ParticipantRepo.insertJsonData(data.participants.toString()).orDie()

            unit
        }
    }
}