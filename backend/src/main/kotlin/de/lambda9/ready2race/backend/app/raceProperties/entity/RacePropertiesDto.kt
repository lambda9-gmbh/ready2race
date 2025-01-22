package de.lambda9.ready2race.backend.app.raceProperties.entity

import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import java.math.BigDecimal

data class RacePropertiesDto(
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
    val participationFee: BigDecimal,
    val rentalFee: BigDecimal,
    val raceCategory: RaceCategoryDto?,
    val namedParticipants: List<NamedParticipantForRaceDto>
)