package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.entity.RacePropertiesHasNamedParticipantDto
import java.util.*

data class RaceRequest (
    val raceProperties: RacePropertiesDto,
    val template: UUID?,
    val namedParticipantList: List<RacePropertiesHasNamedParticipantDto>
)