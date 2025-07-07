package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceForEventRegistrationSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.InvoiceForEventRegistration
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceForEventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE_DOWNLOAD
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE_FOR_EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object InvoiceRepo {

    fun InvoiceForEventRegistration.searchFields() = listOf(INVOICE_NUMBER)

    fun create(record: InvoiceRecord) = INVOICE.insertReturning(record) { ID }

    fun update(id: UUID, f: InvoiceRecord.() -> Unit) = INVOICE.update(f) { ID.eq(id) }

    fun getDownload(id: UUID) = INVOICE_DOWNLOAD.selectOne { ID.eq(id) }

    fun getForRegistration(id: UUID) = INVOICE_FOR_EVENT_REGISTRATION.selectOne { ID.eq(id) }

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            fetchCount(
                this,
                search.metaSearch(searchFields()),
            )
        }
    }

    fun page(
        params: PaginationParameters<InvoiceForEventRegistrationSort>,
    ): JIO<List<InvoiceForEventRegistrationRecord>> = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun countForEvent(
        eventId: UUID,
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            fetchCount(
                this,
                DSL.and(
                    EVENT.eq(eventId),
                    search.metaSearch(searchFields()),
                )
            )
        }
    }

    fun pageForEvent(
        eventId: UUID,
        params: PaginationParameters<InvoiceForEventRegistrationSort>,
    ): JIO<List<InvoiceForEventRegistrationRecord>> = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                }
                .fetch()
        }
    }

    fun countForRegistration(
        registrationId: UUID,
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            fetchCount(
                this,
                DSL.and(
                    EVENT_REGISTRATION.eq(registrationId),
                    search.metaSearch(searchFields()),
                )
            )
        }
    }

    fun pageForRegistration(
        registrationId: UUID,
        params: PaginationParameters<InvoiceForEventRegistrationSort>,
    ): JIO<List<InvoiceForEventRegistrationRecord>> = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT_REGISTRATION.eq(registrationId)
                }
                .fetch()
        }
    }

}