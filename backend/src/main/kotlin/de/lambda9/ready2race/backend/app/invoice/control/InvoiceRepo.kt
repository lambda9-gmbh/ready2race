package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object InvoiceRepo {

    fun create(
        record: InvoiceRecord,
    ): JIO<UUID> = Jooq.query {
        with(INVOICE) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

}