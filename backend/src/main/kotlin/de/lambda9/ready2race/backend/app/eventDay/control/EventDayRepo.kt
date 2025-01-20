package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.database.generated.tables.EventDay
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.http.PaginationParameters
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


    fun count(
        eventId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY) {
            fetchCount(this, DSL.and(EVENT_DAY.EVENT.eq(eventId), search.metaSearch(searchFields())))
        }
    }

    fun page(
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
    ): JIO<Unit> = Jooq.query {
        with(EVENT_DAY) {
            selectFrom(this)
                .where(ID.eq(eventDayId))
                .fetchOne()
                ?.apply(f)
                ?.update()
        }
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