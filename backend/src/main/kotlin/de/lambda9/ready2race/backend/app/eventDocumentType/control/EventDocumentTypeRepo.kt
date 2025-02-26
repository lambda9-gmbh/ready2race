package de.lambda9.ready2race.backend.app.eventDocumentType.control

import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeSort
import de.lambda9.ready2race.backend.database.generated.tables.EventDocumentType
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentTypeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_TYPE
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object EventDocumentTypeRepo {

    private fun EventDocumentType.searchFields() = listOf(NAME)

    fun create(record: EventDocumentTypeRecord) = EVENT_DOCUMENT_TYPE.insertReturning(record) { ID }

    fun update(id: UUID, f: EventDocumentTypeRecord.() -> Unit) = EVENT_DOCUMENT_TYPE.update(f) { ID.eq(id) }

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(EVENT_DOCUMENT_TYPE) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<EventDocumentTypeSort>,
    ): JIO<List<EventDocumentTypeRecord>> = Jooq.query {
        with(EVENT_DOCUMENT_TYPE) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun delete(
        id: UUID,
    ): JIO<Int> = Jooq.query {
        with(EVENT_DOCUMENT_TYPE) {
            deleteFrom(this)
                .where(ID.eq(id))
                .execute()
        }
    }
}