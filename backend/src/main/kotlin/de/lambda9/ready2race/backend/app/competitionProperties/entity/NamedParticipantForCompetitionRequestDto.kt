package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators
import de.lambda9.ready2race.backend.validation.validators.IntValidators.notNegative
import java.util.UUID

data class NamedParticipantForCompetitionRequestDto(
    val namedParticipant: UUID,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::countMales validate notNegative,
            this::countFemales validate notNegative,
            this::countNonBinary validate notNegative,
            this::countMixed validate notNegative,
            ValidationResult.anyOf(
                this::countMales validate IntValidators.min(1),
                this::countFemales validate IntValidators.min(1),
                this::countNonBinary validate IntValidators.min(1),
                this::countMixed validate IntValidators.min(1),
            )
        )

    companion object{
        val example get() = NamedParticipantForCompetitionRequestDto(
            namedParticipant = UUID.randomUUID(),
            countMales = 0,
            countFemales = 0,
            countNonBinary = 0,
            countMixed = 1,
        )
    }
}

