package de.lambda9.ready2race.backend.app.invoice.entity

import java.math.BigDecimal

data class EventInvoicesInfoDto(
    val totalAmount: BigDecimal,
    val paidAmount: BigDecimal,
    val producing: Boolean,
)
