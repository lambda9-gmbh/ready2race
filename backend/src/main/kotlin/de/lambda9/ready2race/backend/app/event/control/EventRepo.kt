package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.event.control.EventRepo.update
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.Event
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object EventRepo {

    private fun Event.searchFields() = listOf(NAME, REGISTRATION_AVAILABLE_FROM, REGISTRATION_AVAILABLE_TO, DESCRIPTION)

    fun create(record: EventRecord) = EVENT.insertReturning(record) { ID }

    fun exists(id: UUID) = EVENT.exists { ID.eq(id) }

    fun update(id: UUID, f: EventRecord.() -> Unit) = EVENT.update(f) { ID.eq(id) }

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