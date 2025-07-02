package de.lambda9.ready2race.backend.app.invoice.entity

import java.time.LocalDateTime
import java.util.UUID

data class InvoiceDto(
    val id: UUID,
    val invoiceNumber: String,
    val createdAt: LocalDateTime,
)
