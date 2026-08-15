package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService.fullName
import de.lambda9.ready2race.backend.app.invoice.entity.EventInvoicesInfoDto
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceContactDto
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceDto
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventInvoicesInfoRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceForEventRegistrationRecord
import de.lambda9.tailwind.core.KIO

fun InvoiceForEventRegistrationRecord.toDto(
    contacts: List<AppUserRecord> = emptyList(),
): App<Nothing, InvoiceDto> = KIO.ok(
    InvoiceDto(
        id = id!!,
        invoiceNumber = invoiceNumber!!,
        billedToOrganization = billedToOrganization,
        billedToName = billedToName,
        billedToContacts = contacts.map {
            InvoiceContactDto(
                name = it.fullName(),
                email = it.email,
            )
        },
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