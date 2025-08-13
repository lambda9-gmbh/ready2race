package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import java.util.*

data class ParticipantRequirementCheckForEventUpsertDto(
    val requirementId: UUID,
    val approvedParticipants: List<CheckedParticipantRequirement>,
    val namedParticipantId: UUID? = null

) : Validatable {
    override fun validate(): ValidationResult =
        this::approvedParticipants validate noDuplicates(CheckedParticipantRequirement::id)

    companion object {
        val example
            get() = ParticipantRequirementCheckForEventUpsertDto(
                requirementId = UUID.randomUUID(),
                approvedParticipants = listOf(
                    CheckedParticipantRequirement(
                        id = UUID.randomUUID(),
                        note = null
                    ),
                    CheckedParticipantRequirement(
                        id = UUID.randomUUID(),
                        note = "unter Vorbehalt"
                    )
                ),
                namedParticipantId = null
            )
    }
}