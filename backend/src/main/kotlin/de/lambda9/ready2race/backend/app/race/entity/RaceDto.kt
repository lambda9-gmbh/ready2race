package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import java.util.*

data class RaceDto(
    val id: UUID,
    val event: UUID,
    val properties: RacePropertiesDto,
    val template: UUID?
)