package de.lambda9.ready2race.backend.app.raceProperties.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validateAllElements
import de.lambda9.ready2race.backend.validation.validators.BigDecimalValidators
import de.lambda9.ready2race.backend.validation.validators.IntValidators
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
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
            this::identifier.validate { notBlank },
            this::name.validate { notBlank },
            this::countMales.validate { IntValidators.notNegative },
            this::countFemales.validate { IntValidators.notNegative },
            this::countNonBinary.validate { IntValidators.notNegative },
            this::countMixed.validate { IntValidators.notNegative },
            this::participationFee.validate { BigDecimalValidators.notNegative },
            this::rentalFee.validate { BigDecimalValidators.notNegative },
            this::namedParticipants.validateAllElements()
        )
}
