package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION_FOR_INVOICE
import de.lambda9.ready2race.backend.database.selectOne
import java.util.UUID

object EventRegistrationForInvoiceRepo {

    fun get(id: UUID) = EVENT_REGISTRATION_FOR_INVOICE.selectOne { ID.eq(id) }

}