package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE
import de.lambda9.ready2race.backend.database.insertReturning

object InvoiceRepo {

    fun create(record: InvoiceRecord) = INVOICE.insertReturning(record) { ID }

}