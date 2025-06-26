package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.beforeNow
import de.lambda9.ready2race.backend.database.generated.tables.records.ProduceInvoiceForRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PRODUCE_INVOICE_FOR_REGISTRATION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import kotlin.time.Duration

object ProduceInvoiceForRegistrationRepo {

    fun create(records: List<ProduceInvoiceForRegistrationRecord>) = PRODUCE_INVOICE_FOR_REGISTRATION.insert(records)

    fun getAndLockNext(
        retryAfterError: Duration,
    ): JIO<ProduceInvoiceForRegistrationRecord?> = Jooq.query {
        with(PRODUCE_INVOICE_FOR_REGISTRATION) {
            selectFrom(this)
                .where(LAST_ERROR_AT.isNull)
                .or(LAST_ERROR_AT.lt(retryAfterError.beforeNow()))
                .orderBy(CREATED_AT.asc())
                .forUpdate()
                .skipLocked()
                .fetchAny()
        }
    }
}