package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.EventDay
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object EventDayRepo {

    private fun EventDay.searchFields() = listOf(DATE, NAME, DESCRIPTION)

    fun create(record: EventDayRecord) = EVENT_DAY.insertReturning(record) { ID }

    fun create(records: List<EventDayRecord>) = EVENT_DAY.insert(records)

    fun exists(id: UUID) = EVENT_DAY.exists { ID.eq(id) }

    fun update(id: UUID, f: EventDayRecord.() -> Unit) = EVENT_DAY.update(f) { ID.eq(id) }

    fun delete(id: UUID) = EVENT_DAY.delete { ID.eq(id) }

    fun getByEvent(eventId: UUID) = EVENT_DAY.select { EVENT.eq(eventId) }

    fun countByEvent(
        eventId: UUID,
        search: String?,
        scope: Privilege.Scope?
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY) {
            fetchCount(
                this,
                DSL.and(
                    EVENT_DAY.EVENT.eq(eventId),
                    search.metaSearch(searchFields()),
                    filterScope(scope, this)
                )
            )
        }
    }

    fun countByEventAndCompetition(
        eventId: UUID,
        competitionId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY) {
            fetchCount(
                this, DSL.and(
                    EVENT.eq(eventId).and(
                        ID.`in`(
                            select(EVENT_DAY_HAS_COMPETITION.EVENT_DAY)
                                .from(EVENT_DAY_HAS_COMPETITION)
                                .where(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(competitionId))
                        )
                    ), search.metaSearch(searchFields())
                )
            )
        }
    }

    fun pageByEvent(
        eventId: UUID,
        params: PaginationParameters<EventDaySort>,
        scope: Privilege.Scope?
    ): JIO<List<EventDayRecord>> = Jooq.query {
        with(EVENT_DAY) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId).and(
                        filterScope(scope, this)
                    )
                }
                .fetch()
        }
    }

    fun pageByEventAndCompetition(
        eventId: UUID,
        competitionId: UUID,
        params: PaginationParameters<EventDaySort>
    ): JIO<List<EventDayRecord>> = Jooq.query {
        with(EVENT_DAY) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                        .and(
                            ID.`in`(
                                select(EVENT_DAY_HAS_COMPETITION.EVENT_DAY)
                                    .from(EVENT_DAY_HAS_COMPETITION)
                                    .where(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(competitionId))
                            )
                        )
                }
                .fetch()
        }
    }

    fun getEventDay(
        eventDayId: UUID,
        scope: Privilege.Scope?
    ): JIO<EventDayRecord?> = Jooq.query {
        with(EVENT_DAY) {
            selectFrom(this)
                .where(
                    ID.eq(eventDayId).and(
                        filterScope(scope, this)
                    )
                )
                .fetchOne()
        }
    }

    fun findUnknown(
        eventDays: List<UUID>
    ): JIO<List<UUID>> = Jooq.query {
        val found = with(EVENT_DAY) {
            select(ID)
                .from(this)
                .where(DSL.or(eventDays.map { ID.eq(it) }))
                .fetch { it.value1() }
        }
        eventDays.filter { !found.contains(it) }
    }

    private fun filterScope(
        scope: Privilege.Scope?,
        eventDay: EventDay
    ) = if (scope == Privilege.Scope.GLOBAL) DSL.trueCondition() else DSL.exists(
        DSL.select(EVENT.ID)
            .from(EVENT)
            .where(EVENT.ID.eq(eventDay.EVENT))
            .and(EVENT.PUBLISHED.eq(true))
    )

    fun getAsJson(eventId: UUID) = EVENT_DAY.selectAsJson { EVENT.eq(eventId) }

    fun insertJsonData(data: String) = EVENT_DAY.insertJsonData(data)
}