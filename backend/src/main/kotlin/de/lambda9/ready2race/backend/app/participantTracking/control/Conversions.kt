package de.lambda9.ready2race.backend.app.participantTracking.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.TeamForScanOverviewDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.TeamParticipantDto
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingViewRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime

fun CompetitionRegistrationTeamRecord.toTeamForScanOverviewDtos() = KIO.ok(
    TeamForScanOverviewDto(
        competitionRegistrationId = competitionRegistrationId!!,
        competitionId = competitionId!!,
        competitionIdentifier = competitionIdentifier!!,
        competitionName = competitionName!!,
        clubId = clubId!!,
        clubName = clubName!!,
        teamName = teamName,
        participants = participants!!.filterNotNull().map { p ->
            TeamParticipantDto(
                participantId = p.participantId!!,
                firstName = p.firstname!!,
                lastName = p.lastname!!,
                year = p.year!!,
                gender = p.gender!!,
                roleId = p.roleId!!,
                role = p.role!!,
                external = p.external!!,
                externalClubName = p.externalClubName,
                currentStatus = p.trackings!!.maxByOrNull { it!!.scannedAt!! }?.scanType.let {
                    if (it != null) ParticipantScanType.valueOf(
                        it
                    ) else null
                },
                lastScanAt = p.trackings!!.maxByOrNull { it!!.scannedAt!! }?.scannedAt
            )
        }
    )
)

fun TeamForScanOverviewDto.toParticipantForExecutionDtos(): App<Nothing, List<ParticipantForExecutionDto>> = KIO.ok(
    participants.map { participant ->
        ParticipantForExecutionDto(
            id = participant.participantId,
            namedParticipantId = participant.roleId,
            namedParticipantName = participant.role,
            firstName = participant.firstName,
            lastName = participant.lastName,
            year = participant.year,
            gender = participant.gender,
            clubId = clubId,
            clubName = clubName,
            competitionRegistrationId = competitionRegistrationId,
            competitionRegistrationName = teamName,
            external = participant.external,
            externalClubName = participant.externalClubName,
        )
    }
)

fun ParticipantForExecutionDto.toTeamForScanOverviewDto(
    currentStatus: ParticipantScanType?,
    lastScanAt: LocalDateTime?
): App<Nothing, TeamParticipantDto> = KIO.ok(
    TeamParticipantDto(
        participantId = id,
        firstName = firstName,
        lastName = lastName,
        year = year,
        gender = gender,
        external = external ?: false,
        externalClubName = externalClubName,
        roleId = namedParticipantId,
        role = namedParticipantName,
        currentStatus = currentStatus,
        lastScanAt = lastScanAt
    )
)

fun ParticipantTrackingViewRecord.toDto(): App<Nothing, ParticipantTrackingDto> = KIO.ok(
    ParticipantTrackingDto(
        id = id!!,
        eventId = eventId!!,
        participantId = participantId!!,
        firstName = firstname!!,
        lastName = lastname!!,
        year = year!!,
        gender = gender!!,
        clubId = clubId!!,
        clubName = clubName!!,
        external = external!!,
        externalClubName = externalClubName,
        scanType = if (scanType != null) ParticipantScanType.valueOf(
            scanType!!
        ) else null,
        scannedAt = scannedAt,
        lastScanBy = if(scannedById != null && scannedByFirstname != null && scannedByLastname != null) AppUserNameDto(
            id = scannedById!!,
            firstname = scannedByFirstname!!,
            lastname = scannedByLastname!!
        ) else null
    )
)