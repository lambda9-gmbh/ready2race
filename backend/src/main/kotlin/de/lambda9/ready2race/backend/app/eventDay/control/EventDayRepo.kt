package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.EventDay
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_HAS_COMPETITION
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object EventDayRepo {

    private fun EventDay.searchFields() = listOf(DATE, NAME, DESCRIPTION)

    fun create(record: EventDayRecord) = EVENT_DAY.insertReturning(record) { ID }

    fun exists(id: UUID) = EVENT_DAY.exists { ID.eq(id) }

    fun update(id: UUID, f: EventDayRecord.() -> Unit) = EVENT_DAY.update(f) { ID.eq(id) }

    fun delete(id: UUID) = EVENT_DAY.delete { ID.eq(id) }

    fun countByEvent(
        eventId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY) {
            fetchCount(this, DSL.and(EVENT_DAY.EVENT.eq(eventId), search.metaSearch(searchFields())))
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
        params: PaginationParameters<EventDaySort>
    ): JIO<List<EventDayRecord>> = Jooq.query {
        with(EVENT_DAY) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
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
        eventDayId: UUID
    ): JIO<EventDayRecord?> = Jooq.query {
        with(EVENT_DAY) {
            selectFrom(this)
                .where(ID.eq(eventDayId))
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
}