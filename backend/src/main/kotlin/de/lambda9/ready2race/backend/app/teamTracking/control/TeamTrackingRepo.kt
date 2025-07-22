package de.lambda9.ready2race.backend.app.teamTracking.control

import de.lambda9.ready2race.backend.app.teamTracking.entity.ScanType
import de.lambda9.ready2race.backend.app.teamTracking.entity.TeamParticipantDto
import de.lambda9.ready2race.backend.app.teamTracking.entity.TeamStatusWithParticipantsDto
import de.lambda9.ready2race.backend.database.generated.tables.records.TeamStatusWithParticipantsRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TeamTrackingRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.TEAM_STATUS_WITH_PARTICIPANTS
import de.lambda9.ready2race.backend.database.generated.tables.references.TEAM_TRACKING
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object TeamTrackingRepo {

    fun insert(record: TeamTrackingRecord) = TEAM_TRACKING.insertReturning(record) { ID }

    fun getCurrentStatusForTeam(competitionRegistrationId: UUID, eventId: UUID): JIO<ScanType?> = Jooq.query {
        selectFrom(TEAM_TRACKING)
            .where(
                TEAM_TRACKING.COMPETITION_REGISTRATION_ID.eq(competitionRegistrationId)
                    .and(TEAM_TRACKING.EVENT_ID.eq(eventId))
            )
            .orderBy(TEAM_TRACKING.SCANNED_AT.desc())
            .limit(1)
            .fetchOne()
            ?.scanType
            ?.let { ScanType.valueOf(it) }
    }

    private fun List<TeamStatusWithParticipantsRecord>.internalGroup() = this.groupBy { it.competitionRegistrationId }
        .map { (_, records) ->
            val firstRecord = records.first()
            TeamStatusWithParticipantsDto(
                id = firstRecord.competitionRegistrationId!!.toString(),
                competitionRegistrationId = firstRecord.competitionRegistrationId!!.toString(),
                eventRegistrationId = firstRecord.eventRegistration!!.toString(),
                competitionId = firstRecord.competition!!.toString(),
                clubName = firstRecord.club!!,
                teamName = firstRecord.teamName ?: "",
                participants = records.map { record ->
                    TeamParticipantDto(
                        participantId = record.participantId!!.toString(),
                        firstname = record.firstname!!,
                        lastname = record.lastname!!,
                        year = record.year!!,
                        gender = record.gender!!,
                        role = record.namedPariticpantName
                    )
                },
                currentStatus = firstRecord.currentStatus?.let { ScanType.valueOf(it) },
                lastScanAt = firstRecord.lastScanAt?.toString(),
                scannedBy = firstRecord.scannedBy?.toString()
            )
        }

    fun getTeamWithParticipantsAndStatus(competitionRegistrationId: UUID): JIO<TeamStatusWithParticipantsDto?> =
        Jooq.query {
            val teamRecords = selectFrom(TEAM_STATUS_WITH_PARTICIPANTS)
                .where(TEAM_STATUS_WITH_PARTICIPANTS.COMPETITION_REGISTRATION_ID.eq(competitionRegistrationId))
                .fetch()
                .toList()

            teamRecords.internalGroup()
                .firstOrNull()
        }

    fun getTeamsByParticipantId(participantId: UUID, eventId: UUID): JIO<List<TeamStatusWithParticipantsDto>> =
        Jooq.query {
            val teamRecords = selectFrom(TEAM_STATUS_WITH_PARTICIPANTS)
                .where(
                    TEAM_STATUS_WITH_PARTICIPANTS.PARTICIPANT_ID.eq(participantId)
                        .and(
                            TEAM_STATUS_WITH_PARTICIPANTS.EVENT_REGISTRATION.`in`(
                                select(EVENT_REGISTRATION.ID)
                                    .from(EVENT_REGISTRATION)
                                    .where(EVENT_REGISTRATION.EVENT.eq(eventId))
                            )
                        )
                )
                .fetch()

            val competitionRegistrationIds = teamRecords.map { it.competitionRegistrationId }.distinct()

            val allTeamRecords = selectFrom(TEAM_STATUS_WITH_PARTICIPANTS)
                .where(TEAM_STATUS_WITH_PARTICIPANTS.COMPETITION_REGISTRATION_ID.`in`(competitionRegistrationIds))
                .fetch()

            allTeamRecords.internalGroup()
        }
}