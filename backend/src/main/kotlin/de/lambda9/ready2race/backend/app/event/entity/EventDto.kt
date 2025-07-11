package de.lambda9.ready2race.backend.app.event.entity

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class EventDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val location: String?,
    val registrationAvailableFrom: LocalDateTime?,
    val registrationAvailableTo: LocalDateTime?,
    val invoicePrefix: String?,
    val published: Boolean?,
    val invoicesProduced: LocalDateTime?,
    val paymentDueBy: LocalDate?,
    val registrationCount: Int?,
    val registrationsFinalized: Boolean,
)