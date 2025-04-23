package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.*

data class CompetitionRegistrationTeamUpsertDto(
    val id: UUID?,
    val clubId: UUID?,
    val optionalFees: List<UUID>?,
    val namedParticipants: List<CompetitionRegistrationNamedParticipantUpsertDto>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = CompetitionRegistrationTeamUpsertDto(
                id = UUID.randomUUID(),
                clubId = UUID.randomUUID(),
                optionalFees = emptyList(),
                namedParticipants = emptyList(),
            )
    }
}