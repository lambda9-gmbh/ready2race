package de.lambda9.ready2race.backend.app.substitution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

data class SubstitutionRequest(
    val competitionRegistrationId: UUID,
    val competitionSetupRound: UUID,
    val participantOut: UUID,
    val participantIn: UUID,
    val reason: String?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: validate

    companion object {
        val example
            get() = SubstitutionRequest(
                competitionRegistrationId = UUID.randomUUID(),
                competitionSetupRound = UUID.randomUUID(),
                participantOut = UUID.randomUUID(),
                participantIn = UUID.randomUUID(),
                reason = "Reason for the substitution",
            )
    }
}