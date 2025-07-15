package de.lambda9.ready2race.backend.app.substitution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.*

data class SubstitutionHasParticipantRequirementRequest(
    val participantRequirement: UUID,
    val approved: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: validate

    companion object {
        val example
            get() = SubstitutionHasParticipantRequirementRequest(
                participantRequirement = UUID.randomUUID(),
                approved = true,
            )
    }
}