package de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.entity

import de.lambda9.ready2race.backend.app.participantCount.entity.ParticipantCountDto

data class RacePropertiesHasNamedParticipantDto (
    val namedParticipant : String,
    val participantCount: ParticipantCountDto
)