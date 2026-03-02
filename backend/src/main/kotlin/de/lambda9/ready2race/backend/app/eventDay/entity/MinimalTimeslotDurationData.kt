package de.lambda9.ready2race.backend.app.eventDay.entity

import java.time.LocalDate
import java.time.LocalTime

data class MinimalTimeslotDurationData(
    val startTime: LocalTime,
    val matchDuration: Int,
    val matchGapsDuration: Int,
    val date: LocalDate
)
