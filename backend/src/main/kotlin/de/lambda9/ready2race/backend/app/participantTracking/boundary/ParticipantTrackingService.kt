package de.lambda9.ready2race.backend.app.participantTracking.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.toParticipantForExecutionDto
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.control.participantDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.participantTracking.control.*
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo.insert
import de.lambda9.ready2race.backend.app.participantTracking.entity.*
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
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

        val currentStatus = !ParticipantTrackingRepo
            .get(participantId, eventId)
            .orDie()
            .map { list ->
                list.maxByOrNull { it.scannedAt!! }?.scanType
            }
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

        !insert(record).orDie()

        noData
    }

    fun page(
        eventId: UUID,
        params: PaginationParameters<ParticipantTrackingSort>,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<Nothing, ApiResponse.Page<ParticipantTrackingDto, ParticipantTrackingSort>> = KIO.comprehension {
        val total = !ParticipantTrackingRepo.count(params.search, eventId, user, scope).orDie()
        val page = !ParticipantTrackingRepo.page(params, eventId, user, scope).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
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

        val participantsForExecution = competitionRegistrationTeams.map { team ->
            team.participants!!.filterNotNull().map {
                !it.toParticipantForExecutionDto(
                    clubId = team.clubId!!,
                    clubName = team.clubName!!,
                    registrationName = team.teamName
                )
            }
        }.flatten()

        val currentlyParticipatingParticipants = !SubstitutionService.getParticipantsCurrentlyParticipatingHelper(
            registrationParticipants = participantsForExecution,
            substitutions = competitionRegistrationTeams.flatMap { it.substitutions!!.filterNotNull() }
        )

        val teamsWithParticipant =
            !competitionRegistrationTeams
                .filter { team ->
                    team.participants!!.any { it!!.participantId == qrCodeRecord.participant!! }
                        || (currentlyParticipatingParticipants.find { it.id == qrCodeRecord.participant!! }?.competitionRegistrationId == team.competitionRegistrationId)
                }.traverse { it.toTeamForScanOverviewDtos() }

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