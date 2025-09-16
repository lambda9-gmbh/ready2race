package de.lambda9.ready2race.backend.app.webDAV.entity.event

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class EventExport(
    val id: UUID,
    val name: String,
    val description: String?,
    val location: String?,
    val published: Boolean?,
    val registrationAvailableFrom: LocalDateTime?,
    val registrationAvailableTo: LocalDateTime?,
    val invoicePrefix: String?,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?,
    val paymentDueBy: LocalDate?,
    val invoicesProduced: LocalDateTime?,
    val lateRegistrationAvailableTo: LocalDateTime?,
    val lateInvoicesProduced: LocalDateTime?,
    val latePaymentDueBy: LocalDate?,
    val mixedTeamTerm: String?,
)