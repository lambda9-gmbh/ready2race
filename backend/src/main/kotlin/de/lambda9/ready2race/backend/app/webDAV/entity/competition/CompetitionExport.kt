package de.lambda9.ready2race.backend.app.webDAV.entity.competition

import java.time.LocalDateTime
import java.util.*

data class CompetitionExport(
    val id: UUID,
    val event: UUID,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)