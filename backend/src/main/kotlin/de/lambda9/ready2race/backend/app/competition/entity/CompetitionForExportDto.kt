package de.lambda9.ready2race.backend.app.competition.entity

import java.util.*

data class CompetitionForExportDto(
    val id: UUID,
    val identifier: String,
    val name: String,
)