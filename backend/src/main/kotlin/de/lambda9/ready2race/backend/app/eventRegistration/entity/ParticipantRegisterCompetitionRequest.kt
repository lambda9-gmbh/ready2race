package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.*

data class ParticipantRegisterCompetitionRequest(
    val competitionId: UUID,
    val optionalFees: List<UUID>?,
    val ratingCategory: UUID?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = ParticipantRegisterCompetitionRequest(
                competitionId = UUID.randomUUID(),
                optionalFees = listOf(UUID.randomUUID()),
                ratingCategory = UUID.randomUUID(),
            )
    }
}