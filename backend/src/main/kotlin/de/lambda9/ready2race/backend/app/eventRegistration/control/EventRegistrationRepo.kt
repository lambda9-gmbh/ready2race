package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.app.eventRegistration.entity.*
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.EventRegistrationsView
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationsViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import java.util.*

object EventRegistrationRepo {

    private fun EventRegistrationsView.searchFields() = listOf(EVENT_NAME)

    fun getView(id: UUID) = EVENT_REGISTRATIONS_VIEW.selectOne { ID.eq(id) }

    fun create(record: EventRegistrationRecord) = EVENT_REGISTRATION.insertReturning(record) { ID }

    fun delete(id: UUID) = EVENT_REGISTRATION.delete { ID.eq(id) }

    fun getIdsByEvent(
        eventId: UUID
    ): JIO<List<UUID>> = Jooq.query {
        with(EVENT_REGISTRATION) {
            select(ID)
                .from(this)
                .where(EVENT.eq(eventId))
                .fetch {
                    it.value1()
                }
        }
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

    fun getEventRegistrationInfo(eventId: UUID): JIO<EventRegistrationInfoDto?> = Jooq.query {

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

    fun getEventRegistrationForUpdate(eventId: UUID, clubId: UUID) = Jooq.query {

        val fees = selectFeesForEventRegistration()

        val singleCompetitions = selectSingleCompetitionsForEventRegistration(fees)

        val namedParticipants = selectNamedParticipantsForEventRegistration()

        val teams = selectTeamsForEventRegistration(fees, namedParticipants, clubId)

        val teamCompetitions = selectTeamCompetitionsForEventRegistration(teams)

        val participants = selectParticipantsForEventRegistration(singleCompetitions, clubId)

        select(
            participants,
            teamCompetitions,
            EVENT_REGISTRATION.MESSAGE
        )
            .from(EVENT)
            .leftJoin(EVENT_REGISTRATION)
            .on(EVENT_REGISTRATION.EVENT.eq(EVENT.ID).and(EVENT_REGISTRATION.CLUB.eq(clubId)))
            .where(EVENT.ID.eq(eventId))
            .fetch {
                EventRegistrationUpsertDto(
                    it[participants],
                    it[teamCompetitions],
                    it[EVENT_REGISTRATION.MESSAGE]
                )
            }.firstOrNull()

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
        COMPETITION_VIEW.IDENTIFIER_PREFIX,
        COMPETITION_VIEW.IDENTIFIER_SUFFIX,
        COMPETITION_VIEW.NAME,
        COMPETITION_VIEW.SHORT_NAME,
        COMPETITION_VIEW.DESCRIPTION,
        COMPETITION_VIEW.CATEGORY_NAME,
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
                    it[COMPETITION_VIEW.IDENTIFIER_PREFIX]!! + (it[COMPETITION_VIEW.IDENTIFIER_SUFFIX] ?: ""),
                    it[COMPETITION_VIEW.NAME]!!,
                    it[COMPETITION_VIEW.SHORT_NAME],
                    it[COMPETITION_VIEW.DESCRIPTION],
                    it[COMPETITION_VIEW.CATEGORY_NAME],
                    it[namedParticipants],
                    it[fees],
                    it[competitionDays]
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
                    it[FEE_FOR_COMPETITION.AMOUNT]!!
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


    private fun selectParticipantsForEventRegistration(
        singleCompetitions: Field<MutableList<CompetitionRegistrationSingleUpsertDto>>,
        clubId: UUID
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
            it!!.map {
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
        }

    private fun selectTeamCompetitionsForEventRegistration(teams: Field<MutableList<CompetitionRegistrationTeamUpsertDto>>) =
        DSL.select(
            COMPETITION_VIEW.ID,
            teams
        ).from(COMPETITION_VIEW)
            .where(COMPETITION_VIEW.TOTAL_COUNT.greaterThan(1))
            .and(COMPETITION_VIEW.EVENT.eq(EVENT.ID))
            .orderBy(COMPETITION_VIEW.NAME)
            .asMultiset("teamCompetitions")
            .convertFrom {
                it!!.map {
                    CompetitionRegistrationUpsertDto(
                        it[COMPETITION_VIEW.ID]!!,
                        it[teams],
                    )
                }
            }

    private fun selectTeamsForEventRegistration(
        fees: Field<MutableList<UUID>>,
        namedParticipants: Field<MutableList<CompetitionRegistrationNamedParticipantUpsertDto>>,
        clubId: UUID
    ) = DSL.select(
        COMPETITION_REGISTRATION.ID,
        fees,
        namedParticipants
    )
        .from(COMPETITION_REGISTRATION)
        .where(COMPETITION_REGISTRATION.COMPETITION.eq(COMPETITION_VIEW.ID))
        .and(COMPETITION_REGISTRATION.CLUB.eq(clubId))
        .asMultiset("teams")
        .convertFrom {
            it!!.map {
                CompetitionRegistrationTeamUpsertDto(
                    it[COMPETITION_REGISTRATION.ID]!!,
                    null,
                    it[fees],
                    it[namedParticipants]
                )
            }
        }

    private fun selectNamedParticipantsForEventRegistration() = DSL.select(
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
            it!!.map {
                CompetitionRegistrationNamedParticipantUpsertDto(
                    it[COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT]!!,
                    it[DSL.arrayAgg(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT)].filterNotNull()
                        .toList()
                )
            }
        }

    private fun selectSingleCompetitionsForEventRegistration(fees: Field<MutableList<UUID>>) = DSL.select(
        COMPETITION_VIEW.ID,
        fees
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
        .asMultiset("singleCompetitions")
        .convertFrom {
            it!!.map {
                CompetitionRegistrationSingleUpsertDto(
                    it[COMPETITION_VIEW.ID]!!,
                    it[fees]
                )
            }
        }

    private fun selectFeesForEventRegistration() = DSL.select(
        COMPETITION_REGISTRATION_OPTIONAL_FEE.FEE
    ).from(COMPETITION_REGISTRATION_OPTIONAL_FEE)
        .where(COMPETITION_REGISTRATION_OPTIONAL_FEE.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
        .asMultiset("fees")
        .convertFrom { it.map { it[COMPETITION_REGISTRATION_OPTIONAL_FEE.FEE] } }


}