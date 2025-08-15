package de.lambda9.ready2race.backend.app.results.entity

import java.util.UUID

data class CompetitionChoiceDto(
    val id: UUID,
    val identifier: String,
    val name: String,
    val shortName: String?,
    val category: String?,
)
