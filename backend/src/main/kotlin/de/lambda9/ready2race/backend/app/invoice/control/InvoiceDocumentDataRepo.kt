package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE_DOCUMENT_DATA
import de.lambda9.ready2race.backend.database.insertReturning

object InvoiceDocumentDataRepo {

    fun create(record: InvoiceDocumentDataRecord) = INVOICE_DOCUMENT_DATA.insertReturning(record) { INVOICE }
}