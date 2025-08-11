package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.util.UUID

class AssignRequirementToNamedParticipantDto(
    val requirementId: UUID,
    val qrCodeRequired: Boolean
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::requirementId validate notNull,
        this::qrCodeRequired validate notNull
    )

    companion object {
        val example = AssignRequirementToNamedParticipantDto(
            requirementId = UUID.randomUUID(),
            qrCodeRequired = false
        )
    }
}