package de.lambda9.ready2race.backend.app.webDAV.entity.event

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class EventDayExport(
    val id: UUID,
    val event: UUID,
    val date: LocalDate,
    val name: String?,
    val description: String?,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)