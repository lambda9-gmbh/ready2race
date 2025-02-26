package de.lambda9.ready2race.backend.app.competitionCategory.entity

import java.util.UUID

data class CompetitionCategoryDto(
    val id: UUID,
    val name: String,
    val description: String?,
)