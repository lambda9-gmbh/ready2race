package de.lambda9.ready2race.backend.app.eventDay.entity

import java.time.LocalTime
import java.util.UUID

data class TimeslotDto(
    val id: UUID,
    val eventDay: UUID,
    val name: String,
    val description: String?,
    val competitionReference: UUID?,
    val roundReference: UUID?,
    val matchReference: UUID?,
    val startTime: LocalTime,
    val endTime: LocalTime
)
