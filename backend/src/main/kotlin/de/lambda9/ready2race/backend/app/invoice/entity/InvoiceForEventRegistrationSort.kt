package de.lambda9.ready2race.backend.app.invoice.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.INVOICE_FOR_EVENT_REGISTRATION
import org.jooq.Field

enum class InvoiceForEventRegistrationSort: Sortable {
    INVOICE_NUMBER,
    BILLED_TO_ORGANIZATION,
    TOTAL_AMOUNT,
    CREATED_AT;

    override fun toFields(): List<Field<*>> = when(this) {
        INVOICE_NUMBER -> listOf(INVOICE_FOR_EVENT_REGISTRATION.INVOICE_NUMBER_PREFIX, INVOICE_FOR_EVENT_REGISTRATION.INVOICE_NUMBER_SUFFIX)
        BILLED_TO_ORGANIZATION -> listOf(INVOICE_FOR_EVENT_REGISTRATION.BILLED_TO_ORGANIZATION, INVOICE_FOR_EVENT_REGISTRATION.BILLED_TO_NAME)
        TOTAL_AMOUNT -> listOf(INVOICE_FOR_EVENT_REGISTRATION.TOTAL_AMOUNT)
        CREATED_AT -> listOf(INVOICE_FOR_EVENT_REGISTRATION.CREATED_AT)
    }
}