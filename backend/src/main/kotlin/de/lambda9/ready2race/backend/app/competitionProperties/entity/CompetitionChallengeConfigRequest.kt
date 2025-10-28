package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.time.LocalDateTime

data class CompetitionChallengeConfigRequest(
    val resultConfirmationImageRequired: Boolean,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = CompetitionChallengeConfigRequest(
                resultConfirmationImageRequired = true,
                startAt = LocalDateTime.now(),
                endAt = LocalDateTime.now().plusDays(1),
            )
    }
}

