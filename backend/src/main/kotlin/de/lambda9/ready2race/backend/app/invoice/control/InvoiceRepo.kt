package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceForEventRegistrationSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.InvoiceForEventRegistration
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceForEventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.util.UUID

object InvoiceRepo {

    fun InvoiceForEventRegistration.searchFields() = listOf(INVOICE_NUMBER)

    fun create(record: InvoiceRecord) = INVOICE.insertReturning(record) { ID }

    fun update(id: UUID, f: InvoiceRecord.() -> Unit) = INVOICE.update(f) { ID.eq(id) }

    fun getDownload(id: UUID) = INVOICE_DOWNLOAD.selectOne { ID.eq(id) }

    fun getClubForRegistration(id: UUID) = INVOICE_FOR_EVENT_REGISTRATION.selectOne({ CLUB }) { ID.eq(id) }

    fun getEventInvoicesInfo(eventId: UUID) = EVENT_INVOICES_INFO.selectOne { EVENT.eq(eventId) }

    fun getByEvents(eventIds: List<UUID>) = INVOICE_DOWNLOAD_FOR_EVENT.select { EVENT.`in`(eventIds) }

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
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): JIO<Int> = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            fetchCount(
                this,
                DSL.and(
                    EVENT.eq(eventId),
                    search.metaSearch(searchFields()),
                    filterScope(scope, user.club)
                )
            )
        }
    }

    fun pageForEvent(
        eventId: UUID,
        params: PaginationParameters<InvoiceForEventRegistrationSort>,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): JIO<List<InvoiceForEventRegistrationRecord>> = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            selectFrom(this)
                .page(params, searchFields()) {
                    DSL.and(
                        EVENT.eq(eventId),
                        filterScope(scope, user.club)
                    )
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

    private fun filterScope(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) INVOICE_FOR_EVENT_REGISTRATION.CLUB.eq(clubId) else DSL.trueCondition()

}