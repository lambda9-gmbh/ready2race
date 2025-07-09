package de.lambda9.ready2race.backend.app.invoice.entity

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class InvoiceDto(
    val id: UUID,
    val invoiceNumber: String,
    val totalAmount: BigDecimal,
    val createdAt: LocalDateTime,
    val paidAt: LocalDateTime?,
)
