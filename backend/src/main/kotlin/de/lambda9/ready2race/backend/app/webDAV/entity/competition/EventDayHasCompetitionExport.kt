package de.lambda9.ready2race.backend.app.webDAV.entity.competition

import java.time.LocalDateTime
import java.util.*

data class EventDayHasCompetitionExport(
    val eventDay: UUID,
    val competition: UUID,
    val createdAt: LocalDateTime,
    val createdBy: UUID?
)