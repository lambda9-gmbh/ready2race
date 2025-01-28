package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.database.generated.tables.EventDay
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_HAS_RACE
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object EventDayRepo {

    private fun EventDay.searchFields() = listOf(DATE, NAME, DESCRIPTION)


    fun create(
        record: EventDayRecord
    ): JIO<UUID> = Jooq.query {
        with(EVENT_DAY) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }


    fun countByEvent(
        eventId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY) {
            fetchCount(this, DSL.and(EVENT_DAY.EVENT.eq(eventId), search.metaSearch(searchFields())))
        }
    }

    fun countByEventAndRace(
        eventId: UUID,
        raceId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY) {
            fetchCount(
                this, DSL.and(
                    EVENT.eq(eventId).and(
                        ID.`in`(
                            select(EVENT_DAY_HAS_RACE.EVENT_DAY)
                                .from(EVENT_DAY_HAS_RACE)
                                .where(EVENT_DAY_HAS_RACE.RACE.eq(raceId))
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

    fun pageByEventAndRace(
        eventId: UUID,
        raceId: UUID,
        params: PaginationParameters<EventDaySort>
    ): JIO<List<EventDayRecord>> = Jooq.query {
        with(EVENT_DAY) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                        .and(
                            ID.`in`(
                                select(EVENT_DAY_HAS_RACE.EVENT_DAY)
                                    .from(EVENT_DAY_HAS_RACE)
                                    .where(EVENT_DAY_HAS_RACE.RACE.eq(raceId))
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

    fun update(
        eventDayId: UUID,
        f: EventDayRecord.() -> Unit
    ): JIO<Boolean> = Jooq.query {
        with(EVENT_DAY) {
            (selectFrom(this)
                .where(ID.eq(eventDayId))
                .fetchOne() ?: return@query false)
                .apply(f)
                .update()
        }
        true
    }

    fun delete(
        eventDayId: UUID
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY) {
            deleteFrom(this)
                .where(ID.eq(eventDayId))
                .execute()
        }
    }
}