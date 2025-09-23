package de.lambda9.ready2race.backend.app.event.entity

import java.util.*

data class EventForExportDto(
    val id: UUID,
    val name: String,
    val competitions: List<CompetitionForExportDto>
) {
    data class CompetitionForExportDto(
        val id: UUID,
        val identifier: String,
        val name: String,
    )
}