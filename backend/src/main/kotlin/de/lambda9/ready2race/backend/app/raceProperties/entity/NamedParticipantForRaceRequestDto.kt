package de.lambda9.ready2race.backend.app.raceProperties.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.notNegative
import java.util.UUID

data class NamedParticipantForRaceRequestDto(
    val namedParticipant: UUID,
    val required: Boolean,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
): Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::countMales.validate { notNegative },
            this::countFemales.validate { notNegative },
            this::countNonBinary.validate { notNegative },
            this::countMixed.validate { notNegative },
        )
}
