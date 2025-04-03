package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.*

data class ParticipantRequirementCheckForEventUpsertDto(
    val requirementId: UUID,
    val approvedParticipants: List<UUID>

) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = ParticipantRequirementCheckForEventUpsertDto(
                requirementId = UUID.randomUUID(),
                approvedParticipants = listOf(UUID.randomUUID(), UUID.randomUUID())
            )
    }
}