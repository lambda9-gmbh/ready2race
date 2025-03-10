package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.records.InvoicePositionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE_POSITION
import de.lambda9.ready2race.backend.database.insert

object InvoicePositionRepo {

    fun create(records: List<InvoicePositionRecord>) = INVOICE_POSITION.insert(records)

}