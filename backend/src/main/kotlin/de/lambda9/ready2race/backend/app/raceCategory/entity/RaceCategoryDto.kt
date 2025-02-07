package de.lambda9.ready2race.backend.app.raceCategory.entity

import java.util.UUID

data class RaceCategoryDto(
    val id: UUID,
    val name: String,
    val description: String?,
)