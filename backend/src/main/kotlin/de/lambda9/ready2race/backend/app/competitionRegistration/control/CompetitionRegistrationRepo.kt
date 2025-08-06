package de.lambda9.ready2race.backend.app.competitionRegistration.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationFeeDto
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationNamedParticipantDto
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationSort
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationTeamDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.OpenForRegistrationType
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventDto
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.findOneBy
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import java.util.*

object CompetitionRegistrationRepo {

    private val searchFieldsForCompetition = listOf(CLUB.NAME, COMPETITION_REGISTRATION.NAME, RATING_CATEGORY.NAME)

    fun create(record: CompetitionRegistrationRecord) = COMPETITION_REGISTRATION.insertReturning(record) { ID }

    // TODO: @What?: Why also competitionId? id is already unique
    fun findByIdAndCompetitionId(id: UUID, competitionId: UUID) =
        COMPETITION_REGISTRATION.findOneBy { ID.eq(id).and(COMPETITION.eq(competitionId)) }

    fun getByClub(clubId: UUID) = COMPETITION_REGISTRATION.select { CLUB.eq(clubId) }

    fun update(id: UUID, f: CompetitionRegistrationRecord.() -> Unit) = COMPETITION_REGISTRATION.update(f) { ID.eq(id) }

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

    fun deleteForEventRegistrationUpdate(eventRegistrationId: UUID, type: OpenForRegistrationType) =
        COMPETITION_REGISTRATION.delete {
            DSL.and(
                COMPETITION_REGISTRATION.EVENT_REGISTRATION.eq(eventRegistrationId),
                when (type) {
                    OpenForRegistrationType.REGULAR -> COMPETITION_REGISTRATION.IS_LATE.isFalse
                    OpenForRegistrationType.LATE -> COMPETITION_REGISTRATION.IS_LATE.isTrue
                    OpenForRegistrationType.CLOSED -> DSL.falseCondition()
                }
            )
        }

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
        search: String?,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): JIO<Int> = Jooq.query {
        fetchCount(
            COMPETITION_REGISTRATION.join(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB)),
            COMPETITION_REGISTRATION.COMPETITION.eq(competitionId)
                .and(filterScope(scope, user.club))
                .and(search.metaSearch(searchFieldsForCompetition))
        )
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

        select(
            COMPETITION_REGISTRATION.ID,
            COMPETITION_REGISTRATION.NAME,
            COMPETITION_REGISTRATION.CLUB,
            CLUB.NAME,
            optionalFees,
            namedParticipants,
            COMPETITION_REGISTRATION.IS_LATE,
            RATING_CATEGORY.ID,
            RATING_CATEGORY.NAME,
            RATING_CATEGORY.DESCRIPTION,
            COMPETITION_REGISTRATION.CREATED_AT,
            COMPETITION_REGISTRATION.UPDATED_AT
        )
            .from(COMPETITION_REGISTRATION)
            .join(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB)) // also user for sort + search
            .leftJoin(RATING_CATEGORY).on(RATING_CATEGORY.ID.eq(COMPETITION_REGISTRATION.RATING_CATEGORY)) // also user for sort + search
            .page(params, searchFieldsForCompetition) {
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
                    isLate = it[COMPETITION_REGISTRATION.IS_LATE]!!,
                    ratingCategory = it[RATING_CATEGORY.ID]?.run {
                        RatingCategoryDto(
                            id = this,
                            name = it[RATING_CATEGORY.NAME]!!,
                            description = it[RATING_CATEGORY.DESCRIPTION]
                        )
                    },
                    createdAt = it[COMPETITION_REGISTRATION.CREATED_AT]!!,
                    updatedAt = it[COMPETITION_REGISTRATION.UPDATED_AT]!!
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
        PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME
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
            PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME
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
                    participantRequirementsChecked = emptyList()
                )
            }
        }

    private fun filterScope(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) COMPETITION_REGISTRATION.CLUB.eq(clubId) else DSL.trueCondition()

}