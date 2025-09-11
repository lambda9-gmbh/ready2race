package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse

data class DataClubsExport(
    val clubs: List<ClubExport>,
    val participants: List<ParticipantExport>
) {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val clubs = !ClubRepo.all().orDie()
                .map { list -> !list.traverse { it.toExport() } }
            val participants = !ParticipantRepo.all().orDie()
                .map { list -> !list.traverse { it.toExport() } }

            val exportData = DataClubsExport(
                clubs = clubs,
                participants = participants
            )

            val json = !WebDAVService.serializeDataExport(record, exportData, WebDAVExportType.DB_CLUBS)

            KIO.ok(File(name = "clubs.json", bytes = json))
        }
    }
}