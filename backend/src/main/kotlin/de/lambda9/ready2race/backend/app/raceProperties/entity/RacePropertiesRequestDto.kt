package de.lambda9.ready2race.backend.app.raceProperties.entity

import de.lambda9.ready2race.backend.plugins.Validatable
import io.ktor.server.plugins.requestvalidation.*
import java.math.BigDecimal
import java.util.*

data class RacePropertiesRequestDto(
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
    val raceCategory: UUID?,
    val namedParticipants: List<NamedParticipantForRaceRequestDto>
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid
}
