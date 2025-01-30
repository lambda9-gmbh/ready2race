package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceDocumentRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE_DOCUMENT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object InvoiceDocumentRepo {

    fun create(
        record: InvoiceDocumentRecord
    ): JIO<Unit> = Jooq.query {
        with(INVOICE_DOCUMENT) {
            insertInto(this)
                .set(record)
                .execute()
        }
    }

}