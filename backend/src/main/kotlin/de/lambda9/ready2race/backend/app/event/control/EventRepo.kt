package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.database.generated.tables.Event
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.http.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object EventRepo {

    private fun Event.searchFields() = listOf(NAME, REGISTRATION_AVAILABLE_FROM, REGISTRATION_AVAILABLE_TO)

    fun create(
        record: EventRecord,
    ): JIO<UUID> = Jooq.query {
        with(EVENT) {
            insertInto(this).set(record).returningResult(ID).fetchOne()!!.value1()!!
        }
    }

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(EVENT) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<EventSort>
    ): JIO<List<EventRecord>> = Jooq.query {
        with(EVENT) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun getEvent(
        id: UUID
    ): JIO<EventRecord?> = Jooq.query {
        with(EVENT) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
        }
    }

    fun update(
        id: UUID,
        f: EventRecord.() -> Unit
    ): JIO<Unit> = Jooq.query {
        with(EVENT) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
                ?.apply(f)
                ?.update()
        }
    }

    fun delete(
        id: UUID
    ): JIO<Int> = Jooq.query {
        with(EVENT) {
            deleteFrom(this)
                .where(ID.eq(id))
                .execute()
        }
    }
}