package de.lambda9.ready2race.backend.app.invoice.entity

import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoicePositionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class InvoiceData(
    val eventName: String,
    val invoiceNumber: String,
    val contact: InvoiceContactData,
    val payee: InvoicePayeeData,
    val billedToOrga: String?,
    val billedToName: String,
    val paymentDueBy: LocalDate,
    val positions: List<InvoicePositionData>,
    val createdAt: LocalDateTime,
) {

    data class InvoiceContactData(
        val name: String,
        val street: String,
        val zip: String,
        val city: String,
        val email: String,
    )

    data class InvoicePayeeData(
        val holder: String,
        val iban: String,
        val bic: String,
        val bank: String,
    )

    data class InvoicePositionData(
        val position: Int,
        val item: String,
        val description: String?,
        val quantity: BigDecimal,
        val unitPrice: BigDecimal,
    )

    companion object {
        fun fromPersisted(
            event: EventRecord,
            invoice: InvoiceRecord,
            positions: List<InvoicePositionRecord>,
        ): InvoiceData = InvoiceData(
            eventName = event.name,
            invoiceNumber = invoice.invoiceNumber,
            contact = InvoiceContactData(
                name = invoice.contactName,
                street = invoice.contactStreet,
                zip = invoice.contactZip,
                city = invoice.contactCity,
                email = invoice.contactEmail,
            ),
            payee = InvoicePayeeData(
                holder = invoice.payeeHolder,
                iban = invoice.payeeIban,
                bic = invoice.payeeBic,
                bank = invoice.payeeBank,
            ),
            billedToOrga = invoice.billedToOrganization,
            billedToName = invoice.billedToName,
            paymentDueBy = invoice.paymentDueBy,
            createdAt = invoice.createdAt,
            positions = positions.map {
                InvoicePositionData(
                    position = it.position,
                    item = it.item,
                    description = it.description,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                )
            }
        )
    }
}
