package de.lambda9.ready2race.backend.app.teamTracking.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.teamTracking.control.TeamTrackingRepo
import de.lambda9.ready2race.backend.app.teamTracking.control.TeamTrackingRepo.insert
import de.lambda9.ready2race.backend.app.teamTracking.entity.*
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.TeamTrackingRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.JIO
import java.time.LocalDateTime
import java.util.*

object TeamTrackingService {

    fun handleTeamCheckIn(
        competitionRegistrationId: UUID,
        eventId: UUID,
        scannedBy: UUID?
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        // Check if team exists
        val teamRegistration = !CompetitionRegistrationRepo.findById(competitionRegistrationId).orDie()
        !KIO.failOn(teamRegistration == null) { TeamTrackingError.TeamNotFound }

        // Check current status
        val currentStatus = !TeamTrackingRepo.getCurrentStatusForTeam(competitionRegistrationId, eventId).orDie()

        if (currentStatus == ScanType.ENTRY) {
            KIO.ok(
                ApiResponse.Dto(
                    TeamTrackingScanDto(
                        competitionRegistrationId = competitionRegistrationId.toString(),
                        eventId = eventId.toString(),
                        scanType = ScanType.ENTRY,
                        success = false,
                        message = "Team is already checked in",
                        teamName = teamRegistration!!.name
                    )
                )
            )
        } else {
            // Record entry
            !insertTeamTracking(competitionRegistrationId, eventId, scannedBy, ScanType.ENTRY).orDie()
            val teamStatus = !TeamTrackingRepo.getTeamWithParticipantsAndStatus(competitionRegistrationId).orDie()

            KIO.ok(
                ApiResponse.Dto(
                    TeamTrackingScanDto(
                competitionRegistrationId = competitionRegistrationId.toString(),
                eventId = eventId.toString(),
                scanType = ScanType.ENTRY,
                success = true,
                message = "Team checked in successfully",
                teamName = teamRegistration!!.name,
                currentStatus = teamStatus?.let {
                    TeamStatusDto(
                        competitionRegistrationId = it.competitionRegistrationId,
                        teamName = it.teamName,
                        currentStatus = it.currentStatus,
                        lastScanAt = it.lastScanAt,
                        scannedBy = it.scannedBy
                    )
                }
            )))
        }
    }

    fun handleTeamCheckOut(
        competitionRegistrationId: UUID,
        eventId: UUID,
        scannedBy: UUID?
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        // Check if team exists
        val teamRegistration = !CompetitionRegistrationRepo.findById(competitionRegistrationId).orDie()
        !KIO.failOn(teamRegistration == null) { TeamTrackingError.TeamNotFound }

        // Check current status
        val currentStatus = !TeamTrackingRepo.getCurrentStatusForTeam(competitionRegistrationId, eventId).orDie()

        if (currentStatus != ScanType.ENTRY) {
            KIO.ok(
                ApiResponse.Dto(
                    TeamTrackingScanDto(
                        competitionRegistrationId = competitionRegistrationId.toString(),
                        eventId = eventId.toString(),
                        scanType = ScanType.EXIT,
                        success = false,
                        message = "Team is not checked in",
                        teamName = teamRegistration!!.name
                    )
                )
            )
        } else {
            // Record exit
            !insertTeamTracking(competitionRegistrationId, eventId, scannedBy, ScanType.EXIT).orDie()
            val teamStatus = !TeamTrackingRepo.getTeamWithParticipantsAndStatus(competitionRegistrationId).orDie()

            KIO.ok(
                ApiResponse.Dto(
                    TeamTrackingScanDto(
                competitionRegistrationId = competitionRegistrationId.toString(),
                eventId = eventId.toString(),
                scanType = ScanType.EXIT,
                success = true,
                message = "Team checked out successfully",
                teamName = teamRegistration!!.name,
                currentStatus = teamStatus?.let {
                    TeamStatusDto(
                        competitionRegistrationId = it.competitionRegistrationId,
                        teamName = it.teamName,
                        currentStatus = it.currentStatus,
                        lastScanAt = it.lastScanAt,
                        scannedBy = it.scannedBy
                    )
                }
            )))
        }
    }

    fun getTeamsByParticipantQrCode(qrCode: String, eventId: UUID): App<ServiceError, ApiResponse> = KIO.comprehension {
        // First find the participant associated with the QR code
        val qrCodeRecord = !QrCodeRepo.findByCode(qrCode).orDie()
        !KIO.failOn(qrCodeRecord == null) { TeamTrackingError.QrCodeNotFound }
        !KIO.failOn(qrCodeRecord!!.participant == null) { TeamTrackingError.QrCodeNotAssociatedWithParticipant }

        // Find all teams this participant belongs to
        val teams = !TeamTrackingRepo.getTeamsByParticipantId(qrCodeRecord.participant!!, eventId).orDie()
        KIO.ok(ApiResponse.ListDto(teams))
    }

    fun insertTeamTracking(
        competitionRegistrationId: UUID,
        eventId: UUID,
        scannedBy: UUID?,
        scanType: ScanType
    ): JIO<Unit> = KIO.comprehension {
        val now = LocalDateTime.now()
        val id = UUID.randomUUID()

        val record = TeamTrackingRecord(
            id = id,
            competitionRegistrationId = competitionRegistrationId,
            eventId = eventId,
            scanType = scanType.name,
            createdAt = now,
            scannedBy = scannedBy,
            scannedAt = now,
        )

        insert(record).orDie().map {}
    }
}