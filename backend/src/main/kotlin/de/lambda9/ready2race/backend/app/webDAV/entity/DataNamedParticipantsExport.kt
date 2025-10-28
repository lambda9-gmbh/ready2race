package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataNamedParticipantsExport(
    val namedParticipants: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val namedParticipants = !NamedParticipantRepo.allAsJson().orDie()

            val json =
                !WebDAVExportService.serializeDataExportNew(record, mapOf("namedParticipants" to namedParticipants))

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_NAMED_PARTICIPANTS), bytes = json))
        }

        fun importData(data: DataNamedParticipantsExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                !NamedParticipantRepo.insertJsonData(data.namedParticipants.toString()).orDie()

                unit
            }
    }
}