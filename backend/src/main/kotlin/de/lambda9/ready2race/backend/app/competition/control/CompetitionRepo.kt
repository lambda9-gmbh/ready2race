package de.lambda9.ready2race.backend.app.competition.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionSortable
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionForClubView
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionPublicView
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionView
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*

object CompetitionRepo {

    private fun CompetitionView.searchFields() =
        listOf(
            NAME,
            SHORT_NAME,
            DSL.concat(IDENTIFIER_PREFIX, DSL.coalesce(IDENTIFIER_SUFFIX.cast(String::class.java), "")),
            CATEGORY_NAME
        )

    private fun CompetitionForClubView.searchFields() =
        listOf(NAME, SHORT_NAME, IDENTIFIER, CATEGORY_NAME)

    private fun CompetitionPublicView.searchFields() =
        listOf(NAME, SHORT_NAME, IDENTIFIER, CATEGORY_NAME)

    fun create(record: CompetitionRecord) = COMPETITION.insertReturning(record) { ID }

    fun exists(id: UUID) = COMPETITION.exists { ID.eq(id) }

    fun update(id: UUID, f: CompetitionRecord.() -> Unit) = COMPETITION.update(f) { ID.eq(id) }

    fun delete(id: UUID) = COMPETITION.delete { ID.eq(id) }

    fun isOpenForRegistration(id: UUID, at: LocalDateTime) = Jooq.query {
        fetchExists(
            COMPETITION_VIEW
                .join(EVENT).on(COMPETITION_VIEW.EVENT.eq(EVENT.ID))
                .where(
                    DSL.and(
                        COMPETITION_VIEW.ID.eq(id),
                        DSL.or(
                            DSL.and(
                                COMPETITION_VIEW.LATE_REGISTRATION_ALLOWED.isTrue,
                                EVENT.REGISTRATION_AVAILABLE_TO.le(at),
                                EVENT.LATE_REGISTRATION_AVAILABLE_TO.ge(at),
                            ),
                            DSL.and(
                                COMPETITION_VIEW.LATE_REGISTRATION_ALLOWED.isFalse,
                                EVENT.REGISTRATION_AVAILABLE_FROM.le(at),
                                DSL.or(
                                    EVENT.REGISTRATION_AVAILABLE_TO.isNull,
                                    EVENT.REGISTRATION_AVAILABLE_TO.ge(at),
                                )
                            ),
                        ),
                        EVENT.PUBLISHED.isTrue,
                    )
                )
        )
    }

    fun countWithPropertiesByEventAndEventDay(
        eventId: UUID,
        eventDayId: UUID? = null,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_VIEW) {
            fetchCount(
                this, DSL.and(
                    EVENT.eq(eventId),
                    eventDayId?.let {
                        ID.`in`(
                            select(EVENT_DAY_HAS_COMPETITION.COMPETITION)
                                .from(EVENT_DAY_HAS_COMPETITION)
                                .where(EVENT_DAY_HAS_COMPETITION.EVENT_DAY.eq(it))
                        )
                    } ?: DSL.trueCondition()), search.metaSearch(searchFields())
            )
        }
    }

    fun <S : CompetitionSortable> pageWithPropertiesByEventAndEventDay(
        eventId: UUID,
        eventDayId: UUID? = null,
        params: PaginationParameters<S>,
        scope: Privilege.Scope
    ): JIO<List<CompetitionViewRecord>> = Jooq.query {
        with(COMPETITION_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId).and(
                        eventDayId?.let {
                            ID.`in`(
                                select(EVENT_DAY_HAS_COMPETITION.COMPETITION)
                                    .from(EVENT_DAY_HAS_COMPETITION)
                                    .where(EVENT_DAY_HAS_COMPETITION.EVENT_DAY.eq(it))
                            )
                        } ?: DSL.trueCondition())
                        .and(
                            if (scope == Privilege.Scope.GLOBAL) {
                                DSL.trueCondition()
                            } else {
                                DSL.falseCondition()
                            }
                        )
                }
                .fetch()
        }
    }

    fun <S : CompetitionSortable> pageWithPropertiesByEventAndEventDayForClub(
        eventId: UUID,
        eventDayId: UUID? = null,
        params: PaginationParameters<S>,
        user: AppUserWithPrivilegesRecord,
    ): JIO<List<CompetitionForClubViewRecord>> = Jooq.query {
        with(COMPETITION_FOR_CLUB_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId).and(CLUB.eq(user.club))
                        .and(
                            eventDayId?.let {
                                ID.`in`(
                                    select(EVENT_DAY_HAS_COMPETITION.COMPETITION)
                                        .from(EVENT_DAY_HAS_COMPETITION)
                                        .where(EVENT_DAY_HAS_COMPETITION.EVENT_DAY.eq(it))
                                )
                            } ?: DSL.trueCondition())
                }
                .fetch()
        }
    }

    fun countPublicByEventAndEventDay(
        eventId: UUID,
        eventDayId: UUID? = null,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_PUBLIC_VIEW) {
            fetchCount(
                this, DSL.and(
                    EVENT.eq(eventId),
                    eventDayId?.let {
                        ID.`in`(
                            select(EVENT_DAY_HAS_COMPETITION.COMPETITION)
                                .from(EVENT_DAY_HAS_COMPETITION)
                                .where(EVENT_DAY_HAS_COMPETITION.EVENT_DAY.eq(it))
                        )
                    } ?: DSL.trueCondition()), search.metaSearch(searchFields())
            )
        }
    }

    fun <S : CompetitionSortable> pagePublicByEventAndEventDay(
        eventId: UUID,
        eventDayId: UUID? = null,
        params: PaginationParameters<S>,
    ): JIO<List<CompetitionPublicViewRecord>> = Jooq.query {
        with(COMPETITION_PUBLIC_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                        .and(
                            eventDayId?.let {
                                ID.`in`(
                                    select(EVENT_DAY_HAS_COMPETITION.COMPETITION)
                                        .from(EVENT_DAY_HAS_COMPETITION)
                                        .where(EVENT_DAY_HAS_COMPETITION.EVENT_DAY.eq(it))
                                )
                            } ?: DSL.trueCondition())
                }
                .fetch()
        }
    }

    fun getWithProperties(
        competitionId: UUID,
        scope: Privilege.Scope,
    ): JIO<CompetitionViewRecord?> = Jooq.query {
        with(COMPETITION_VIEW) {
            selectFrom(this)
                .where(
                    ID.eq(competitionId).and(
                        if (scope == Privilege.Scope.GLOBAL) {
                            DSL.trueCondition()
                        } else {
                            DSL.falseCondition()
                        }
                    )
                )
                .fetchOne()
        }
    }

    fun getWithPropertiesForClub(
        competitionId: UUID,
        user: AppUserWithPrivilegesRecord,
    ): JIO<CompetitionForClubViewRecord?> = Jooq.query {
        with(COMPETITION_FOR_CLUB_VIEW) {
            selectFrom(this)
                .where(ID.eq(competitionId).and(CLUB.eq(user.club)))
                .fetchOne()
        }
    }

    fun getPublic(
        competitionId: UUID,
    ): JIO<CompetitionPublicViewRecord?> = Jooq.query {
        with(COMPETITION_PUBLIC_VIEW) {
            selectFrom(this)
                .where(ID.eq(competitionId))
                .fetchOne()
        }
    }

    fun findUnknown(
        competitions: List<UUID>
    ): JIO<List<UUID>> = Jooq.query {
        val found = with(COMPETITION) {
            select(ID)
                .from(this)
                .where(DSL.or(competitions.map { ID.eq(it) }))
                .fetch { it.value1() }
        }
        competitions.filter { !found.contains(it) }
    }

}