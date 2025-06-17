package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import java.util.UUID

data class UpdateCompetitionMatchTeamRequest(
    val registrationId: UUID,
    val startNumber: Int,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::startNumber validate min(1),
    )

    companion object {
        val example
            get() = UpdateCompetitionMatchTeamRequest(
                registrationId = UUID.randomUUID(),
                startNumber = 1,
            )
    }
}