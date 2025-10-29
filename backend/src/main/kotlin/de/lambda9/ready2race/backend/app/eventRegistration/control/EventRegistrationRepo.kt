package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.app.eventRegistration.entity.*
import de.lambda9.ready2race.backend.app.invoice.entity.RegistrationInvoiceType
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.EventRegistrationsView
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventCompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationsViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import java.util.*

object EventRegistrationRepo {

    private fun EventRegistrationsView.searchFields() = listOf(EVENT_NAME)

    fun getView(id: UUID) = EVENT_REGISTRATIONS_VIEW.selectOne { ID.eq(id) }

    fun getClub(id: UUID) = EVENT_REGISTRATION.selectOne({ CLUB }) { ID.eq(id) }

    fun create(record: EventRegistrationRecord) = EVENT_REGISTRATION.insertReturning(record) { ID }

    fun delete(id: UUID) = EVENT_REGISTRATION.delete { ID.eq(id) }

    fun getIdsForInvoicing(eventId: UUID, type: RegistrationInvoiceType) = EVENT_REGISTRATION.select({ ID }) {
        DSL.and(
            EVENT.eq(eventId),
            DSL.exists(
                DSL.selectFrom(COMPETITION_REGISTRATION)
                    .where(COMPETITION_REGISTRATION.EVENT_REGISTRATION.eq(ID))
                    .and(
                        when (type) {
                            RegistrationInvoiceType.REGULAR -> COMPETITION_REGISTRATION.IS_LATE.isFalse
                            RegistrationInvoiceType.LATE -> COMPETITION_REGISTRATION.IS_LATE.isTrue
                        }
                    )
            )
        )
    }

    fun update(id: UUID, f: EventRegistrationRecord.() -> Unit) =
        EVENT_REGISTRATION.update(f) {
            EVENT_REGISTRATION.ID.eq(id)
        }

    fun countForView(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(EVENT_REGISTRATIONS_VIEW) {
            fetchCount(
                this,
                search.metaSearch(searchFields())
            )
        }
    }

    fun pageForView(
        params: PaginationParameters<EventRegistrationViewSort>
    ): JIO<List<EventRegistrationsViewRecord>> = Jooq.query {
        with(EVENT_REGISTRATIONS_VIEW) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun countForEvent(
        eventId: UUID,
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(EVENT_REGISTRATIONS_VIEW) {
            fetchCount(
                this,
                DSL.and(
                    search.metaSearch(searchFields()),
                    EVENT_ID.eq(eventId)
                )
            )
        }
    }

    fun pageForEvent(
        eventId: UUID,
        params: PaginationParameters<EventRegistrationViewSort>
    ): JIO<List<EventRegistrationsViewRecord>> = Jooq.query {
        with(EVENT_REGISTRATIONS_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT_ID.eq(eventId)
                }
                .fetch()
        }
    }

    fun findByEventAndClub(eventId: UUID, clubId: UUID) =
        EVENT_REGISTRATION.findOneBy { EVENT_REGISTRATION.EVENT.eq(eventId).and(EVENT_REGISTRATION.CLUB.eq(clubId)) }

    fun getRegistrationResult(eventId: UUID) = EVENT_REGISTRATION_RESULT_VIEW.selectOne { ID.eq(eventId) }

    fun getEventRegistrationDocuments(eventId: UUID): JIO<List<EventRegistrationDocumentTypeDto>?> = Jooq.query {

        val documents = selectDocumentsForEventRegistrationInfo()

        val documentTypes = selectDocumentTypesForEventRegistrationInfo(documents)

        select(
            documentTypes
        )
            .from(EVENT)
            .where(EVENT.ID.eq(eventId))
            .fetchOne { it[documentTypes] }
    }

    fun getEventRegistrationInfo(eventId: UUID, type: OpenForRegistrationType): JIO<EventRegistrationInfoDto?> =
        Jooq.query {

            val eventDays = selectEventDaysForEventRegistrationInfo()

            val competitionDays = selectCompetitionDaysForEventRegistrationInfo()

            val fees = selectFeesForEventRegistrationInfo()

            val namedParticipants = selectNamedParticipantsForEventRegistrationInfo()

            val documents = selectDocumentsForEventRegistrationInfo()

            val documentTypes = selectDocumentTypesForEventRegistrationInfo(documents)

            val competitionsSingle = selectCompetitionsForEventRegistrationInfo(
                namedParticipants,
                competitionDays,
                fees,
                COMPETITION_VIEW.TOTAL_COUNT.eq(1),
                "competitionsSingle"
            )

            val competitionsTeam = selectCompetitionsForEventRegistrationInfo(
                namedParticipants,
                competitionDays,
                fees,
                COMPETITION_VIEW.TOTAL_COUNT.greaterThan(1),
                "competitionTeam"
            )

            select(
                EVENT.NAME,
                EVENT.DESCRIPTION,
                EVENT.LOCATION,
                eventDays,
                documentTypes,
                competitionsSingle,
                competitionsTeam
            )
                .from(EVENT)
                .where(EVENT.ID.eq(eventId))
                .fetch {
                    EventRegistrationInfoDto(
                        type,
                        it[EVENT.NAME]!!,
                        it[EVENT.DESCRIPTION],
                        it[EVENT.LOCATION],
                        it[eventDays],
                        it[documentTypes],
                        it[competitionsSingle],
                        it[competitionsTeam],
                    )
                }.firstOrNull()

        }

    enum class RegistrationFilter {
        REGULAR,
        LATE,
        ALL,
    }

    fun getLockedEventRegistration(eventId: UUID, clubId: UUID, type: OpenForRegistrationType) = Jooq.query {

        val filter = when (type) {
            OpenForRegistrationType.REGULAR -> RegistrationFilter.LATE
            OpenForRegistrationType.LATE -> RegistrationFilter.REGULAR
            OpenForRegistrationType.CLOSED -> RegistrationFilter.ALL
        }

        val fees = selectFeesForEventRegistration()

        val singleCompetitions = selectSingleCompetitionsForEventRegistration(fees, filter) {
            CompetitionRegistrationSingleLockedDto(
                it[COMPETITION_VIEW.ID]!!,
                it[fees],
                it[COMPETITION_REGISTRATION.IS_LATE]!!,
                it[COMPETITION_REGISTRATION.RATING_CATEGORY],
            )
        }

        val namedParticipants = selectNamedParticipantsForEventRegistration {
            CompetitionRegistrationNamedParticipantLockedDto(
                it[COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT]!!,
                it[DSL.arrayAgg(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT)].filterNotNull()
            )
        }

        val teams = selectTeamsForEventRegistration(fees, namedParticipants, clubId, filter) {
            CompetitionRegistrationTeamLockedDto(
                it[COMPETITION_REGISTRATION.ID]!!,
                it[fees],
                it[namedParticipants],
                it[COMPETITION_REGISTRATION.IS_LATE]!!,
                it[COMPETITION_REGISTRATION.RATING_CATEGORY],
            )
        }

        val teamCompetitions = selectTeamCompetitionsForEventRegistration(teams) {
            CompetitionRegistrationLockedDto(
                it[COMPETITION_VIEW.ID]!!,
                it[teams],
            )
        }

        val participants = selectParticipantsForEventRegistration(singleCompetitions, clubId) {
            EventRegistrationParticipantLockedDto(
                id = it[PARTICIPANT.ID]!!,
                competitionsSingle = it[singleCompetitions],
            )
        }

        select(
            participants,
            teamCompetitions,
            EVENT_REGISTRATION.MESSAGE
        )
            .from(EVENT)
            .leftJoin(EVENT_REGISTRATION)
            .on(EVENT_REGISTRATION.EVENT.eq(EVENT.ID).and(EVENT_REGISTRATION.CLUB.eq(clubId)))
            .where(EVENT.ID.eq(eventId))
            .fetchOne {
                EventRegistrationLockedDto(
                    it[participants],
                    it[teamCompetitions]
                )
            }
    }

    fun getEventRegistrationForUpdate(eventId: UUID, clubId: UUID, type: OpenForRegistrationType) = Jooq.query {

        val filter = when (type) {
            OpenForRegistrationType.REGULAR -> RegistrationFilter.REGULAR
            OpenForRegistrationType.LATE -> RegistrationFilter.LATE
            OpenForRegistrationType.CLOSED -> null
        }

        if (filter == null) {
            EventRegistrationUpsertDto(emptyList(), emptyList(), null)
        } else {

            val fees = selectFeesForEventRegistration()

            val singleCompetitions = selectSingleCompetitionsForEventRegistration(fees, filter) {
                CompetitionRegistrationSingleUpsertDto(
                    it[COMPETITION_VIEW.ID]!!,
                    it[fees],
                    it[COMPETITION_REGISTRATION.RATING_CATEGORY],
                )
            }

            val namedParticipants = selectNamedParticipantsForEventRegistration {
                CompetitionRegistrationNamedParticipantUpsertDto(
                    it[COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT]!!,
                    it[DSL.arrayAgg(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT)].filterNotNull()
                        .toList()
                )
            }

            val teams = selectTeamsForEventRegistration(fees, namedParticipants, clubId, filter) {
                CompetitionRegistrationTeamUpsertDto(
                    it[COMPETITION_REGISTRATION.ID]!!,
                    null,
                    it[fees],
                    it[namedParticipants],
                    it[COMPETITION_REGISTRATION.RATING_CATEGORY],
                )
            }

            val teamCompetitions = selectTeamCompetitionsForEventRegistration(teams) {
                CompetitionRegistrationUpsertDto(
                    it[COMPETITION_VIEW.ID]!!,
                    it[teams],
                )
            }

            val participants = selectParticipantsForEventRegistration(singleCompetitions, clubId) {
                EventRegistrationParticipantUpsertDto(
                    id = it[PARTICIPANT.ID]!!,
                    isNew = false,
                    hasChanged = false,
                    firstname = it[PARTICIPANT.FIRSTNAME]!!,
                    lastname = it[PARTICIPANT.LASTNAME]!!,
                    year = it[PARTICIPANT.YEAR]!!,
                    gender = it[PARTICIPANT.GENDER]!!,
                    external = it[PARTICIPANT.EXTERNAL],
                    externalClubName = it[PARTICIPANT.EXTERNAL_CLUB_NAME],
                    competitionsSingle = it[singleCompetitions],
                )
            }

            select(
                participants,
                teamCompetitions,
                EVENT_REGISTRATION.MESSAGE
            )
                .from(EVENT)
                .leftJoin(EVENT_REGISTRATION)
                .on(EVENT_REGISTRATION.EVENT.eq(EVENT.ID).and(EVENT_REGISTRATION.CLUB.eq(clubId)))
                .where(EVENT.ID.eq(eventId))
                .fetchOne {
                    EventRegistrationUpsertDto(
                        it[participants],
                        it[teamCompetitions],
                        it[EVENT_REGISTRATION.MESSAGE]
                    )
                }
        }
    }

    private fun selectDocumentTypesForEventRegistrationInfo(
        documents: Field<MutableList<EventRegistrationDocumentFileDto>>,
    ) = DSL.select(
        EVENT_DOCUMENT_TYPE.ID,
        EVENT_DOCUMENT_TYPE.NAME,
        EVENT_DOCUMENT_TYPE.DESCRIPTION,
        EVENT_DOCUMENT_TYPE.CONFIRMATION_REQUIRED,
        documents
    ).from(EVENT_DOCUMENT_TYPE)
        .join(EVENT_DOCUMENT).on(EVENT_DOCUMENT.EVENT_DOCUMENT_TYPE.eq(EVENT_DOCUMENT_TYPE.ID))
        .where(EVENT_DOCUMENT.EVENT.eq(EVENT.ID))
        .groupBy(EVENT_DOCUMENT_TYPE.ID, EVENT_DOCUMENT_TYPE.NAME, EVENT_DOCUMENT_TYPE.CONFIRMATION_REQUIRED)
        .orderBy(EVENT_DOCUMENT_TYPE.CONFIRMATION_REQUIRED.desc(), EVENT_DOCUMENT_TYPE.NAME)
        .asMultiset("documentTypes")
        .convertFrom {
            it.map {
                EventRegistrationDocumentTypeDto(
                    it[EVENT_DOCUMENT_TYPE.ID]!!,
                    it[EVENT_DOCUMENT_TYPE.NAME]!!,
                    it[EVENT_DOCUMENT_TYPE.DESCRIPTION],
                    it[EVENT_DOCUMENT_TYPE.CONFIRMATION_REQUIRED]!!,
                    it[documents]
                )
            }
        }

    private fun selectDocumentsForEventRegistrationInfo() = DSL.select(
        EVENT_DOCUMENT.ID,
        EVENT_DOCUMENT.NAME,
        EVENT_DOCUMENT_TYPE.CONFIRMATION_REQUIRED
    ).from(EVENT_DOCUMENT)
        .where(EVENT_DOCUMENT.EVENT_DOCUMENT_TYPE.eq(EVENT_DOCUMENT_TYPE.ID))
        .asMultiset("documents")
        .convertFrom {
            it.map {
                EventRegistrationDocumentFileDto(
                    it[EVENT_DOCUMENT.ID]!!,
                    it[EVENT_DOCUMENT.NAME]!!,
                )
            }
        }

    private fun selectCompetitionsForEventRegistrationInfo(
        namedParticipants: Field<MutableList<EventRegistrationNamedParticipantDto>>,
        competitionDays: Field<MutableList<UUID>>,
        fees: Field<MutableList<EventRegistrationFeeDto>>,
        condition: Condition,
        alias: String
    ) = DSL.select(
        COMPETITION_VIEW.ID,
        COMPETITION_VIEW.IDENTIFIER,
        COMPETITION_VIEW.NAME,
        COMPETITION_VIEW.SHORT_NAME,
        COMPETITION_VIEW.DESCRIPTION,
        COMPETITION_VIEW.CATEGORY_NAME,
        COMPETITION_VIEW.LATE_REGISTRATION_ALLOWED,
        COMPETITION_VIEW.RATING_CATEGORY_REQUIRED,
        namedParticipants,
        fees,
        competitionDays,
    )
        .from(COMPETITION_VIEW)
        .where(COMPETITION_VIEW.EVENT.eq(EVENT.ID))
        .and(condition)
        .orderBy(COMPETITION_VIEW.NAME)
        .asMultiset(alias)
        .convertFrom {
            it.map {
                EventRegistrationCompetitionDto(
                    it[COMPETITION_VIEW.ID]!!,
                    it[COMPETITION_VIEW.IDENTIFIER]!!,
                    it[COMPETITION_VIEW.NAME]!!,
                    it[COMPETITION_VIEW.SHORT_NAME],
                    it[COMPETITION_VIEW.DESCRIPTION],
                    it[COMPETITION_VIEW.CATEGORY_NAME],
                    it[namedParticipants],
                    it[fees],
                    it[competitionDays],
                    it[COMPETITION_VIEW.LATE_REGISTRATION_ALLOWED]!!,
                    it[COMPETITION_VIEW.RATING_CATEGORY_REQUIRED]!!,
                )
            }
        }

    private fun selectNamedParticipantsForEventRegistrationInfo() = DSL.select(
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.ID,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.NAME,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.DESCRIPTION,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_MALES,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_FEMALES,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_NON_BINARY,
        NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_MIXED
    )
        .from(NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES)
        .where(NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COMPETITION_ID.eq(COMPETITION_VIEW.ID))
        .orderBy(NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.NAME)
        .asMultiset("namedParticipants")
        .convertFrom {
            it!!.map {
                EventRegistrationNamedParticipantDto(
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.ID]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.NAME]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.DESCRIPTION],
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_MALES]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_FEMALES]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_NON_BINARY]!!,
                    it[NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.COUNT_MIXED]!!,
                )
            }
        }

    private fun selectFeesForEventRegistrationInfo() = DSL.select(
        FEE_FOR_COMPETITION.ID,
        FEE_FOR_COMPETITION.NAME,
        FEE_FOR_COMPETITION.DESCRIPTION,
        FEE_FOR_COMPETITION.REQUIRED,
        FEE_FOR_COMPETITION.AMOUNT,
        FEE_FOR_COMPETITION.LATE_AMOUNT,
    )
        .from(FEE_FOR_COMPETITION)
        .where(COMPETITION_VIEW.ID.eq(FEE_FOR_COMPETITION.COMPETITION_ID))
        .orderBy(FEE_FOR_COMPETITION.REQUIRED, FEE_FOR_COMPETITION.NAME)
        .asMultiset("fees")
        .convertFrom {
            it!!.map {
                EventRegistrationFeeDto(
                    it[FEE_FOR_COMPETITION.ID]!!,
                    it[FEE_FOR_COMPETITION.NAME]!!,
                    it[FEE_FOR_COMPETITION.DESCRIPTION],
                    it[FEE_FOR_COMPETITION.REQUIRED]!!,
                    it[FEE_FOR_COMPETITION.AMOUNT]!!,
                    it[FEE_FOR_COMPETITION.LATE_AMOUNT]
                )
            }
        }

    private fun selectCompetitionDaysForEventRegistrationInfo() = DSL.select(
        EVENT_DAY_HAS_COMPETITION.EVENT_DAY,
    )
        .from(EVENT_DAY_HAS_COMPETITION)
        .where(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(COMPETITION_VIEW.ID))
        .asMultiset("competitionDays")
        .convertFrom { it.map { it[EVENT_DAY_HAS_COMPETITION.EVENT_DAY]!! } }

    private fun selectEventDaysForEventRegistrationInfo() = DSL.select(
        EVENT_DAY.ID,
        EVENT_DAY.NAME,
        EVENT_DAY.DATE,
        EVENT_DAY.DESCRIPTION
    )
        .from(EVENT_DAY)
        .where(EVENT_DAY.EVENT.eq(EVENT.ID))
        .asMultiset("eventDays")
        .convertFrom {
            it.map {
                EventRegistrationDayDto(
                    it[EVENT_DAY.ID]!!,
                    it[EVENT_DAY.DATE]!!,
                    it[EVENT_DAY.NAME],
                    it[EVENT_DAY.DESCRIPTION],
                )
            }
        }


    private fun <A, SC> selectParticipantsForEventRegistration(
        singleCompetitions: Field<MutableList<SC>>,
        clubId: UUID,
        convert: (Record) -> A,
    ) = DSL.select(
        PARTICIPANT.ID,
        PARTICIPANT.FIRSTNAME,
        PARTICIPANT.LASTNAME,
        PARTICIPANT.YEAR,
        PARTICIPANT.GENDER,
        PARTICIPANT.EXTERNAL,
        PARTICIPANT.EXTERNAL_CLUB_NAME,
        singleCompetitions
    )
        .from(PARTICIPANT)
        .where(PARTICIPANT.CLUB.eq(clubId))
        .orderBy(PARTICIPANT.FIRSTNAME, PARTICIPANT.LASTNAME)
        .asMultiset("participants")
        .convertFrom {
            it!!.map { convert(it) }
        }

    private fun <A, T> selectTeamCompetitionsForEventRegistration(
        teams: Field<MutableList<T>>,
        convert: (Record) -> A
    ) =
        DSL.select(
            COMPETITION_VIEW.ID,
            teams
        ).from(COMPETITION_VIEW)
            .where(COMPETITION_VIEW.TOTAL_COUNT.greaterThan(1))
            .and(COMPETITION_VIEW.EVENT.eq(EVENT.ID))
            .orderBy(COMPETITION_VIEW.NAME)
            .asMultiset("teamCompetitions")
            .convertFrom {
                it!!.map { convert(it) }
            }

    private fun <A, NP> selectTeamsForEventRegistration(
        fees: Field<MutableList<UUID>>,
        namedParticipants: Field<MutableList<NP>>,
        clubId: UUID,
        filter: RegistrationFilter,
        convert: (Record) -> A
    ) = DSL.select(
        COMPETITION_REGISTRATION.ID,
        COMPETITION_REGISTRATION.IS_LATE,
        COMPETITION_REGISTRATION.RATING_CATEGORY,
        fees,
        namedParticipants
    )
        .from(COMPETITION_REGISTRATION)
        .where(COMPETITION_REGISTRATION.COMPETITION.eq(COMPETITION_VIEW.ID))
        .and(COMPETITION_REGISTRATION.CLUB.eq(clubId))
        .and(
            when (filter) {
                RegistrationFilter.REGULAR -> COMPETITION_REGISTRATION.IS_LATE.isFalse
                RegistrationFilter.LATE -> COMPETITION_REGISTRATION.IS_LATE.isTrue
                RegistrationFilter.ALL -> DSL.trueCondition()
            }
        )
        .asMultiset("teams")
        .convertFrom {
            it!!.map { convert(it) }
        }

    private fun <A> selectNamedParticipantsForEventRegistration(
        convert: (Record) -> A
    ) = DSL.select(
        COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT,
        DSL.arrayAgg(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT)
    )
        .from(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
        .join(NAMED_PARTICIPANT)
        .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
        .where(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
        .groupBy(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT, NAMED_PARTICIPANT.NAME)
        .orderBy(NAMED_PARTICIPANT.NAME)
        .asMultiset("namedParticipants")
        .convertFrom {
            it!!.map { convert(it) }
        }

    private fun <A> selectSingleCompetitionsForEventRegistration(
        fees: Field<MutableList<UUID>>,
        filter: RegistrationFilter,
        convert: (Record) -> A,
    ) = DSL.select(
        COMPETITION_VIEW.ID,
        COMPETITION_REGISTRATION.IS_LATE,
        COMPETITION_REGISTRATION.RATING_CATEGORY,
        fees,
    ).from(COMPETITION_VIEW)
        .join(COMPETITION_REGISTRATION).on(COMPETITION_REGISTRATION.COMPETITION.eq(COMPETITION_VIEW.ID))
        .join(COMPETITION_REGISTRATION_NAMED_PARTICIPANT).on(
            COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(
                COMPETITION_REGISTRATION.ID
            )
        )
        .where(COMPETITION_VIEW.TOTAL_COUNT.eq(1))
        .and(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT.eq(PARTICIPANT.ID))
        .and(COMPETITION_VIEW.EVENT.eq(EVENT.ID))
        .and(
            when (filter) {
                RegistrationFilter.REGULAR -> COMPETITION_REGISTRATION.IS_LATE.isFalse
                RegistrationFilter.LATE -> COMPETITION_REGISTRATION.IS_LATE.isTrue
                RegistrationFilter.ALL -> DSL.trueCondition()
            }
        )
        .asMultiset("singleCompetitions")
        .convertFrom {
            it!!.map { convert(it) }
        }

    private fun selectFeesForEventRegistration() = DSL.select(
        COMPETITION_REGISTRATION_OPTIONAL_FEE.FEE
    ).from(COMPETITION_REGISTRATION_OPTIONAL_FEE)
        .where(COMPETITION_REGISTRATION_OPTIONAL_FEE.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
        .asMultiset("fees")
        .convertFrom { it.map { it[COMPETITION_REGISTRATION_OPTIONAL_FEE.FEE] } }

}