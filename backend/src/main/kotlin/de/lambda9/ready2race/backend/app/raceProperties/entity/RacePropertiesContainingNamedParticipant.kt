package de.lambda9.ready2race.backend.app.raceProperties.entity

import java.util.UUID

data class RacePropertiesContainingNamedParticipant(
    val raceTemplateId: UUID?,
    val raceId: UUID?,
    val name: String,
    val shortName: String?,
)