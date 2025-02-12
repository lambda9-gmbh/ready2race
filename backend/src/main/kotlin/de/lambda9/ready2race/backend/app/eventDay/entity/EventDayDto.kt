package de.lambda9.ready2race.backend.app.eventDay.entity

import java.time.LocalDate
import java.util.*

data class EventDayDto (
    val id: UUID,
    val event: UUID,
    val date: LocalDate,
    val name: String?,
    val description: String?,
)