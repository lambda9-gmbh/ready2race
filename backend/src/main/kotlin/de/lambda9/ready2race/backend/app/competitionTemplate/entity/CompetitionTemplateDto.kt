package de.lambda9.ready2race.backend.app.competitionTemplate.entity

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesDto
import java.util.*

data class CompetitionTemplateDto(
    val id : UUID,
    val properties: CompetitionPropertiesDto
)