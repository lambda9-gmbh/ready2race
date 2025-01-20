package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesWithNamedParticipantListDto
import java.util.*

data class RaceDto(
    val id: UUID,
    val event: UUID,
    val raceProperties: RacePropertiesWithNamedParticipantListDto,
    val template: UUID?
)