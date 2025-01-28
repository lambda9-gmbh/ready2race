package de.lambda9.ready2race.backend.app.raceProperties.entity

import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.BigDecimalValidators
import de.lambda9.ready2race.backend.validation.validators.IntValidators
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validators.collection
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
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::identifier validate notBlank,
            this::name validate notBlank,
            this::countMales validate IntValidators.notNegative,
            this::countFemales validate IntValidators.notNegative,
            this::countNonBinary validate IntValidators.notNegative,
            this::countMixed validate IntValidators.notNegative,
            this::participationFee validate BigDecimalValidators.notNegative,
            this::rentalFee validate BigDecimalValidators.notNegative,
            this::namedParticipants validate collection
        )

    companion object{
        val example get() = RacePropertiesRequestDto(
            identifier = "001",
            name = "Name",
            shortName = "N",
            description = "Description of name",
            countMales = 0,
            countFemales = 0,
            countNonBinary = 0,
            countMixed = 1,
            participationFee = BigDecimal(10),
            rentalFee = BigDecimal(1),
            raceCategory = UUID.randomUUID(),
            namedParticipants = listOf(NamedParticipantForRaceRequestDto.example)
        )
    }
}
