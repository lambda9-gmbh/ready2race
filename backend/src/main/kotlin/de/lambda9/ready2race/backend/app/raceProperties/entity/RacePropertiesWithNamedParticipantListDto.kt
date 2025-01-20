package de.lambda9.ready2race.backend.app.raceProperties.entity

import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto

data class RacePropertiesWithNamedParticipantListDto(
    val raceProperties: RacePropertiesDto,
    val namedParticipantList: List<NamedParticipantDto>
)