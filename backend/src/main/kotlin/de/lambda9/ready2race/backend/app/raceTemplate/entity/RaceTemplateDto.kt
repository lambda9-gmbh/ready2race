package de.lambda9.ready2race.backend.app.raceTemplate.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import java.util.*

data class RaceTemplateDto(
    val id : UUID,
    val properties: RacePropertiesDto
)