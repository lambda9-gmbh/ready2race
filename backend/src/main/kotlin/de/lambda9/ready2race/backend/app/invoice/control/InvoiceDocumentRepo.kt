package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceDocumentRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE_DOCUMENT
import de.lambda9.ready2race.backend.database.insertReturning

object InvoiceDocumentRepo {

    fun create(record: InvoiceDocumentRecord) = INVOICE_DOCUMENT.insertReturning(record) { INVOICE }

}