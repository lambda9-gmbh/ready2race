package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.app.competition.entity.CompetitionForExportDto
import java.util.*

data class EventForExportDto(
    val id: UUID,
    val name: String,
    val competitions: List<CompetitionForExportDto>
)