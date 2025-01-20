package de.lambda9.ready2race.backend.app.eventDay.entity

import java.time.LocalDate

data class EventDayProperties(
    val date: LocalDate,
    val name: String?,
    val description: String?,
)