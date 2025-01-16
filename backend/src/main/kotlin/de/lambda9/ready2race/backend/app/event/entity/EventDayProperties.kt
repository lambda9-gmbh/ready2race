package de.lambda9.ready2race.backend.app.event.entity

import kotlinx.datetime.LocalDateTime

data class EventDayProperties(
    val date: LocalDateTime,
    val name: String?,
    val description: String?,
)