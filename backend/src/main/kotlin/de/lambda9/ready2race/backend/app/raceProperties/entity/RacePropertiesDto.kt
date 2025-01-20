package de.lambda9.ready2race.backend.app.raceProperties.entity

import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.app.participantCount.entity.ParticipantCountDto
import java.math.BigDecimal

data class RacePropertiesDto(
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val participantCount: ParticipantCountDto?,
    val participationFee: BigDecimal,
    val rentalFee: BigDecimal,
    val raceCategory: String?,
)