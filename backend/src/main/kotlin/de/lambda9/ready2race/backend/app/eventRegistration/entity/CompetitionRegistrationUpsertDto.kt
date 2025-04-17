package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.*

data class CompetitionRegistrationUpsertDto(
    val competitionId: UUID,
    val teams: List<CompetitionRegistrationTeamUpsertDto>?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.Valid
}