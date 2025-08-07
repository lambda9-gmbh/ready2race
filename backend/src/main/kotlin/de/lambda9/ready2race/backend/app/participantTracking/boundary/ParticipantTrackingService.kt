package de.lambda9.ready2race.backend.app.participantTracking.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.participantTracking.control.toParticipantForExecutionDtos
import de.lambda9.ready2race.backend.app.participantTracking.control.toTeamForScanOverviewDtos
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo
import de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo.insert
import de.lambda9.ready2race.backend.app.participantTracking.control.toTeamForScanOverviewDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object ParticipantTrackingService {

    fun participantCheckInOut(
        participantId: UUID,
        eventId: UUID,
        userId: UUID,
        checkIn: Boolean,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !ParticipantRepo.exists(participantId).orDie().onFalseFail { ParticipantError.ParticipantNotFound }

        val currentStatus = !ParticipantTrackingRepo.getCurrentStatus(participantId, eventId).orDie()
        !KIO.failOn(currentStatus == ParticipantScanType.ENTRY.name && checkIn) { ParticipantTrackingError.TeamAlreadyCheckedIn }
        !KIO.failOn(currentStatus == ParticipantScanType.EXIT.name && !checkIn) { ParticipantTrackingError.TeamNotCheckedIn }

        val record = ParticipantTrackingRecord(
            id = UUID.randomUUID(),
            participant = participantId,
            event = eventId,
            scanType = if (checkIn) ParticipantScanType.ENTRY.name else ParticipantScanType.EXIT.name,
            scannedBy = userId,
            scannedAt = LocalDateTime.now(),
        )

        insert(record).orDie()

        noData
    }


    fun getByParticipantQrCode(
        qrCode: String,
        eventId: UUID
    ): App<ServiceError, ApiResponse.ListDto<TeamForScanOverviewDto>> = KIO.comprehension {
        // Find participant with QR code
        val qrCodeRecord = !QrCodeRepo.findByCode(qrCode).orDie()
        !KIO.failOn(qrCodeRecord == null) { ParticipantTrackingError.QrCodeNotFound }
        !KIO.failOn(qrCodeRecord!!.participant == null) { ParticipantTrackingError.QrCodeNotAssociatedWithParticipant }

        // Get all registrations for this event
        val competitionRegistrationTeams = !CompetitionRegistrationRepo.getCompetitionRegistrationTeams(eventId).orDie()

        val registrationIdsToSubstitutions = competitionRegistrationTeams.map { team ->
            team.competitionRegistrationId!! to team.substitutions!!.filterNotNull()
        }

        val teamsWithParticipant =
            !competitionRegistrationTeams
                .traverse { it.toTeamForScanOverviewDtos() }
                .map { teams -> teams.filter { team -> team.participants.any { it.participantId == qrCodeRecord.participant!! } } }

        val teamsWithSubstitutions = teamsWithParticipant.map { team ->
            val participants = !team.toParticipantForExecutionDtos()

            // Get current round of the competition to get the substitutions of the current round for this registration
            val currentRoundId = !CompetitionExecutionService.getCurrentRoundId(team.competitionId)
            val substitutionsInRound =
                registrationIdsToSubstitutions.first { it.first == team.competitionRegistrationId }.second.filter {
                    it.competitionSetupRoundId == currentRoundId
                }

            val actuallyParticipating = !CompetitionExecutionService.getActuallyParticipatingParticipants(
                teamParticipants = participants,
                substitutionsForRegistration = substitutionsInRound
            ).map { ps ->
                !ps.traverse { p ->
                    // Get the trackings since the data is lost through the previous function or needs to be fetched for substitutions
                    val knownParticipant =
                        teamsWithParticipant.flatMap { it.participants }.find { it.participantId == p.id }
                    if (knownParticipant == null) {
                        val unknownParticipantTracking = !ParticipantTrackingRepo.get(p.id, eventId).orDie()
                        val lastScan = unknownParticipantTracking.maxByOrNull { it.scannedAt!! }
                        p.toTeamForScanOverviewDto(
                            currentStatus = if (lastScan != null) ParticipantScanType.valueOf(lastScan.scanType!!) else null,
                            lastScanAt = lastScan?.scannedAt
                        )
                    } else {
                        p.toTeamForScanOverviewDto(
                            currentStatus = knownParticipant.currentStatus,
                            lastScanAt = knownParticipant.lastScanAt
                        )
                    }

                }
            }

            team.copy(participants = actuallyParticipating)
        }

        ok(ApiResponse.ListDto(teamsWithSubstitutions))
    }


}