package de.lambda9.ready2race.backend.app.participantTracking.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingChangeDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingChangeType
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingEntryDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingSource
import de.lambda9.ready2race.backend.app.participantTracking.entity.TeamForScanOverviewDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.TeamParticipantDto
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingChangeViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingViewRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun CompetitionRegistrationTeamRecord.toTeamForScanOverviewDtos() = KIO.ok(
    TeamForScanOverviewDto(
        competitionRegistrationId = competitionRegistrationId!!,
        competitionId = competitionId!!,
        competitionIdentifier = competitionIdentifier!!,
        competitionName = competitionName!!,
        checkInOutRequired = checkInOutRequired!!,
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

/** Ein Eintrag für den Verlaufsdialog - dieselbe Sicht wie das Protokoll, ohne die Personendaten. */
fun ParticipantTrackingViewRecord.toEntryDto(): App<Nothing, ParticipantTrackingEntryDto> = KIO.ok(
    ParticipantTrackingEntryDto(
        id = id!!,
        scanType = ParticipantScanType.valueOf(scanType!!),
        scannedAt = scannedAt!!,
        source = ParticipantTrackingSource.valueOf(source!!),
        recordedBy = appUserName(scannedById, scannedByFirstname, scannedByLastname),
        editCount = (editCount ?: 0L).toInt(),
        lastEditedAt = lastEditedAt,
        lastEditedBy = appUserName(lastEditedById, lastEditedByFirstname, lastEditedByLastname),
    )
)

fun ParticipantTrackingChangeViewRecord.toDto(): App<Nothing, ParticipantTrackingChangeDto> = KIO.ok(
    ParticipantTrackingChangeDto(
        id = id!!,
        trackingId = tracking,
        changeType = ParticipantTrackingChangeType.valueOf(changeType!!),
        previousScanType = previousScanType?.let { ParticipantScanType.valueOf(it) },
        previousScannedAt = previousScannedAt,
        newScanType = ParticipantScanType.valueOf(newScanType!!),
        newScannedAt = newScannedAt!!,
        reason = reason!!,
        createdAt = createdAt!!,
        createdBy = appUserName(createdById, createdByFirstname, createdByLastname),
    )
)

/**
 * Ein Name ist nur dann einer, wenn alle drei Teile da sind. Ein gelöschtes Konto hinterlässt in
 * der Spur `null` - dort steht dann kein Name, nicht ein halber.
 */
private fun appUserName(id: UUID?, firstname: String?, lastname: String?): AppUserNameDto? =
    if (id != null && firstname != null && lastname != null) {
        AppUserNameDto(id = id, firstname = firstname, lastname = lastname)
    } else {
        null
    }

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
        lastScanBy = appUserName(scannedById, scannedByFirstname, scannedByLastname),
        source = ParticipantTrackingSource.valueOf(source!!),
        editCount = (editCount ?: 0L).toInt(),
        lastEditedAt = lastEditedAt,
        lastEditedBy = appUserName(lastEditedById, lastEditedByFirstname, lastEditedByLastname),
    )
)