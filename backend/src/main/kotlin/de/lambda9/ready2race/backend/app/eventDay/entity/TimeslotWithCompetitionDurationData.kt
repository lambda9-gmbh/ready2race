package de.lambda9.ready2race.backend.app.eventDay.entity

import kotlinx.datetime.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class TimeslotWithCompetitionDurationData(
    val id: UUID,
    val competitionReference: UUID,
    val roundReference: UUID?,
    val matchReference: UUID?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val matchDuration: Int,
    val matchGapsDuration: Int,
    val date: LocalDate
)
