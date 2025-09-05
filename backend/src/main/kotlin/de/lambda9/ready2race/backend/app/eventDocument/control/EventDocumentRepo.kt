package de.lambda9.ready2race.backend.app.eventDocument.control

import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentViewSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.EventDocumentView
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentDownloadRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_DOWNLOAD
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_VIEW
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object EventDocumentRepo {

    private fun EventDocumentView.searchFields() = listOf(NAME, DOCUMENT_TYPE)

    fun create(record: EventDocumentRecord) = EVENT_DOCUMENT.insertReturning(record) { ID }

    fun update(id: UUID, f: EventDocumentRecord.() -> Unit) = EVENT_DOCUMENT.update(f) { ID.eq(id) }

    fun delete(id: UUID) = EVENT_DOCUMENT.delete { ID.eq(id) }

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(EVENT_DOCUMENT_VIEW) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<EventDocumentViewSort>,
    ): JIO<List<EventDocumentViewRecord>> = Jooq.query {
        with(EVENT_DOCUMENT_VIEW) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun getDownload(id: UUID): JIO<EventDocumentDownloadRecord?> = EVENT_DOCUMENT_DOWNLOAD.selectOne { ID.eq(id) }

    fun getDownloadsByEvent(eventId: UUID): JIO<List<EventDocumentDownloadRecord>> =
        EVENT_DOCUMENT_DOWNLOAD.select { EVENT.eq(eventId) }


    fun getDownloadsByEvents(
        eventIds: List<UUID>,
    ): JIO<List<EventDocumentDownloadRecord>> = EVENT_DOCUMENT_DOWNLOAD.select { EVENT.`in`(eventIds) }

    fun getByEventIds(eventIds: List<UUID>) = EVENT_DOCUMENT.select { EVENT.`in`(eventIds) }
}