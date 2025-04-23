package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesDto
import java.util.*

data class CompetitionDto(
    val id: UUID,
    val event: UUID,
    val properties: CompetitionPropertiesDto,
    val template: UUID?,
    val registrationCount: Long,
)