package de.lambda9.ready2race.backend.app.competitionRegistration.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationFeeDto
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationNamedParticipantDto
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationSort
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationTeamDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventDto
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.findOneBy
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import java.util.*
import de.lambda9.ready2race.backend.app.teamTracking.entity.TeamScanType
import java.time.LocalDateTime

object CompetitionRegistrationRepo {

    fun create(record: CompetitionRegistrationRecord) = COMPETITION_REGISTRATION.insertReturning(record) { ID }

    fun findById(id: UUID) = COMPETITION_REGISTRATION.findOneBy { ID.eq(id) }

    // TODO: @What?: Why also competitionId? id is already unique
    fun findByIdAndCompetitionId(id: UUID, competitionId: UUID) =
        COMPETITION_REGISTRATION.findOneBy { ID.eq(id).and(COMPETITION.eq(competitionId)) }

    fun allForEvent(eventId: UUID): JIO<List<CompetitionRegistrationRecord>> = Jooq.query {
        select(COMPETITION_REGISTRATION)
            .from(COMPETITION_REGISTRATION)
            .join(EVENT_REGISTRATION)
            .on(COMPETITION_REGISTRATION.EVENT_REGISTRATION.eq(EVENT_REGISTRATION.ID))
            .where(EVENT_REGISTRATION.EVENT.eq(eventId))
            .fetch { it.value1() }
    }

    fun delete(
        competitionId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ) = COMPETITION_REGISTRATION.delete { ID.eq(competitionId).and(filterScope(scope, user.club)) }

    fun deleteByEventRegistration(eventRegistrationId: UUID) =
        COMPETITION_REGISTRATION.delete { COMPETITION_REGISTRATION.EVENT_REGISTRATION.eq(eventRegistrationId) }

    fun getClub(id: UUID) = COMPETITION_REGISTRATION.selectOne({ CLUB }) { ID.eq(id) }

    fun getByCompetitionAndClub(competitionId: UUID, clubId: UUID) = COMPETITION_REGISTRATION.select { COMPETITION.eq(competitionId).and(CLUB.eq(clubId)) }

    fun countForCompetitionAndClub(
        competitionId: UUID,
        clubId: UUID,
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_REGISTRATION) {
            fetchCount(
                this,
                DSL.and(
                    COMPETITION.eq(competitionId),
                    CLUB.eq(clubId)
                )
            )
        }
    }

    fun countForCompetition(
        competitionId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_REGISTRATION) {
            fetchCount(
                this,
                COMPETITION_REGISTRATION.COMPETITION.eq(competitionId)
                    .and(filterScope(scope, user.club))
            )
        }
    }

    fun getByCompetitionId(id: UUID): JIO<List<CompetitionRegistrationRecord>> = Jooq.query {
        with(COMPETITION_REGISTRATION){
            selectFrom(this)
                .where(COMPETITION.eq(id))
                .fetch()
        }
    }

    fun pageForCompetition(
        competitionId: UUID,
        params: PaginationParameters<CompetitionRegistrationSort>,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): JIO<List<CompetitionRegistrationTeamDto>> = Jooq.query {

        val optionalFees = selectFees()

        val participants = selectParticipants()

        val namedParticipants = selectNamedParticipants(participants)

        // Subquery to get the latest team tracking data
        val teamTrackingLateral = DSL.lateral(
            DSL.select(
                TEAM_TRACKING.SCAN_TYPE,
                TEAM_TRACKING.SCANNED_AT,
                TEAM_TRACKING.SCANNED_BY
            )
            .from(TEAM_TRACKING)
            .where(TEAM_TRACKING.COMPETITION_REGISTRATION_ID.eq(COMPETITION_REGISTRATION.ID))
            .orderBy(TEAM_TRACKING.SCANNED_AT.desc())
            .limit(1)
        ).`as`("tt")

        select(
            COMPETITION_REGISTRATION.ID,
            COMPETITION_REGISTRATION.NAME,
            COMPETITION_REGISTRATION.CLUB,
            CLUB.NAME,
            optionalFees,
            namedParticipants,
            COMPETITION_REGISTRATION.CREATED_AT,
            COMPETITION_REGISTRATION.UPDATED_AT,
            teamTrackingLateral.field("scan_type", String::class.java),
            teamTrackingLateral.field("scanned_at", LocalDateTime::class.java),
            teamTrackingLateral.field("scanned_by", UUID::class.java)
        )
            .from(COMPETITION_REGISTRATION)
            .join(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
            .leftJoin(teamTrackingLateral).on(DSL.trueCondition())
            .page(params) {
                COMPETITION_REGISTRATION.COMPETITION.eq(competitionId)
                    .and(filterScope(scope, user.club))
            }
            .fetch {
                CompetitionRegistrationTeamDto(
                    id = it[COMPETITION_REGISTRATION.ID]!!,
                    name = it[COMPETITION_REGISTRATION.NAME],
                    clubId = it[COMPETITION_REGISTRATION.CLUB]!!,
                    clubName = it[CLUB.NAME]!!,
                    optionalFees = it[optionalFees],
                    namedParticipants = it[namedParticipants],
                    createdAt = it[COMPETITION_REGISTRATION.CREATED_AT]!!,
                    updatedAt = it[COMPETITION_REGISTRATION.UPDATED_AT]!!,
                    currentStatus = it.get(teamTrackingLateral.field("scan_type", String::class.java))?.let { TeamScanType.valueOf(it) },
                    lastScanAt = it.get(teamTrackingLateral.field("scanned_at", LocalDateTime::class.java)),
                    scannedBy = it.get(teamTrackingLateral.field("scanned_by", UUID::class.java))
                )
            }

    }

    private fun selectFees() = DSL.select(
        COMPETITION_REGISTRATION_OPTIONAL_FEE.FEE,
        FEE.NAME,
    ).from(COMPETITION_REGISTRATION_OPTIONAL_FEE)
        .join(FEE).on(FEE.ID.eq(COMPETITION_REGISTRATION_OPTIONAL_FEE.FEE))
        .where(COMPETITION_REGISTRATION_OPTIONAL_FEE.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
        .asMultiset("fees")
        .convertFrom {
            it.map {
                CompetitionRegistrationFeeDto(
                    it[COMPETITION_REGISTRATION_OPTIONAL_FEE.FEE]!!,
                    it[FEE.NAME]!!
                )
            }
        }

    private fun selectNamedParticipants(participants: Field<MutableList<ParticipantForEventDto>>) = DSL.select(
        NAMED_PARTICIPANT.ID,
        NAMED_PARTICIPANT.NAME,
        participants
    )
        .from(NAMED_PARTICIPANT)
        .join(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
        .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
        .where(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
        .groupBy(NAMED_PARTICIPANT.NAME, NAMED_PARTICIPANT.ID)
        .orderBy(NAMED_PARTICIPANT.NAME, NAMED_PARTICIPANT.ID)
        .asMultiset("namedParticipants")
        .convertFrom {
            it!!.map {
                CompetitionRegistrationNamedParticipantDto(
                    it[NAMED_PARTICIPANT.ID]!!,
                    it[NAMED_PARTICIPANT.NAME]!!,
                    it[participants]
                )
            }
        }


    private fun selectParticipants() = DSL.select(
        PARTICIPANT_FOR_EVENT.ID,
        PARTICIPANT_FOR_EVENT.CLUB_ID,
        PARTICIPANT_FOR_EVENT.CLUB_NAME,
        PARTICIPANT_FOR_EVENT.FIRSTNAME,
        PARTICIPANT_FOR_EVENT.LASTNAME,
        PARTICIPANT_FOR_EVENT.YEAR,
        PARTICIPANT_FOR_EVENT.GENDER,
        PARTICIPANT_FOR_EVENT.EXTERNAL,
        PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME,
        PARTICIPANT_FOR_EVENT.QR_CODE_ID,
        PARTICIPANT_FOR_EVENT.NAMED_PARTICIPANT_IDS
    )
        .from(PARTICIPANT_FOR_EVENT)
        .join(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
        .on(PARTICIPANT_FOR_EVENT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
        .where(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT.eq(NAMED_PARTICIPANT.ID))
        .and(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
        .groupBy(
            PARTICIPANT_FOR_EVENT.ID,
            PARTICIPANT_FOR_EVENT.CLUB_ID,
            PARTICIPANT_FOR_EVENT.CLUB_NAME,
            PARTICIPANT_FOR_EVENT.FIRSTNAME,
            PARTICIPANT_FOR_EVENT.LASTNAME,
            PARTICIPANT_FOR_EVENT.YEAR,
            PARTICIPANT_FOR_EVENT.GENDER,
            PARTICIPANT_FOR_EVENT.EXTERNAL,
            PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME,
            PARTICIPANT_FOR_EVENT.QR_CODE_ID,
            PARTICIPANT_FOR_EVENT.NAMED_PARTICIPANT_IDS
        )
        .orderBy(PARTICIPANT_FOR_EVENT.FIRSTNAME, PARTICIPANT_FOR_EVENT.LASTNAME)
        .asMultiset("participants")
        .convertFrom {
            it.map {
                ParticipantForEventDto(
                    id = it[PARTICIPANT_FOR_EVENT.ID]!!,
                    clubId = it[PARTICIPANT_FOR_EVENT.CLUB_ID]!!,
                    clubName = it[PARTICIPANT_FOR_EVENT.CLUB_NAME]!!,
                    firstname = it[PARTICIPANT_FOR_EVENT.FIRSTNAME]!!,
                    lastname = it[PARTICIPANT_FOR_EVENT.LASTNAME]!!,
                    year = it[PARTICIPANT_FOR_EVENT.YEAR],
                    gender = it[PARTICIPANT_FOR_EVENT.GENDER]!!,
                    external = it[PARTICIPANT_FOR_EVENT.EXTERNAL],
                    externalClubName = it[PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME],
                    participantRequirementsChecked = emptyList(),
                    qrCodeId = it[PARTICIPANT_FOR_EVENT.QR_CODE_ID],
                    namedParticipantIds = it[PARTICIPANT_FOR_EVENT.NAMED_PARTICIPANT_IDS]?.filterNotNull() ?: emptyList()
                )
            }
        }

    private fun filterScope(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) COMPETITION_REGISTRATION.CLUB.eq(clubId) else DSL.trueCondition()

}