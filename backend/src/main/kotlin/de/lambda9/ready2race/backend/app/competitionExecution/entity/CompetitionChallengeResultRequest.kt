package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult


data class CompetitionChallengeResultRequest(
    val result: Int,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = CompetitionChallengeResultRequest(
                result = 958000
            )
    }
}