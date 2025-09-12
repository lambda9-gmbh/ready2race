package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse

data class DataClubsExport(
    val clubs: List<ClubExport>,
    val participants: List<ParticipantExport>
) : WebDAVExportData {
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

            val json = !WebDAVService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_CLUBS), bytes = json))
        }

        fun importData(data: DataClubsExport): App<WebDAVError.Unexpected, Unit> = KIO.comprehension {
            // Club
            try {

            val overlappingClubs = !ClubRepo.getOverlapIds(data.clubs.map { it.id }).orDie()
            val clubRecords = !data.clubs
                .filter { clubData -> !overlappingClubs.any { it == clubData.id } }
                .traverse { it.toRecord() }
            ClubRepo.create(clubRecords).orDie()

            // Participant
            val overlappingParticipants = !ParticipantRepo.getOverlapIds(data.participants.map { it.id }).orDie()
            val participantRecords = !data.participants
                .filter { participantData -> !overlappingParticipants.any { it == participantData.id } }
                .traverse { it.toRecord() }
            !ParticipantRepo.create(participantRecords).orDie()

            } catch (ex: Exception) {
                return@comprehension KIO.fail(WebDAVError.Unexpected)
            }
            unit
        }
    }
}