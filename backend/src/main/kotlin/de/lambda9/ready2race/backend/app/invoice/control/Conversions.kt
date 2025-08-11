package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.invoice.entity.EventInvoicesInfoDto
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceDto
import de.lambda9.ready2race.backend.database.generated.tables.records.EventInvoicesInfoRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceForEventRegistrationRecord
import de.lambda9.tailwind.core.KIO

fun InvoiceForEventRegistrationRecord.toDto(): App<Nothing, InvoiceDto> = KIO.ok(
    InvoiceDto(
        id = id!!,
        invoiceNumber = invoiceNumber!!,
        totalAmount = totalAmount!!,
        createdAt = createdAt!!,
        paidAt = paidAt,
    )
)

fun EventInvoicesInfoRecord.toDto(): App<Nothing, EventInvoicesInfoDto> = KIO.ok(
    EventInvoicesInfoDto(
        totalAmount = totalAmount!!,
        paidAmount = paidAmount!!,
        producing = producing!!,
    )
)