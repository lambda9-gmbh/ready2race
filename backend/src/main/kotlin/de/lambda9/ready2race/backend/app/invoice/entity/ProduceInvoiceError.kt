package de.lambda9.ready2race.backend.app.invoice.entity

import java.util.UUID

sealed interface ProduceInvoiceError {

    data class MissingRecipient(val registrationId: UUID): ProduceInvoiceError
    data object NoOpenJobs : ProduceInvoiceError
    data object NoPositions : ProduceInvoiceError

}