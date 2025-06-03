package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationInvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION_INVOICE
import de.lambda9.ready2race.backend.database.insert

object EventRegistrationInvoiceRepo {

    fun create(record: EventRegistrationInvoiceRecord) = EVENT_REGISTRATION_INVOICE.insert(record)

}