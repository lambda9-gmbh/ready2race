package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryRequest
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import java.util.*

data class UpdateCompetitionMatchTeamResultRequest(
    val registrationId: UUID,
    val place: Int
) : Validatable {
    override fun validate(): ValidationResult = this::place validate min(1)

    companion object{
        val example get() = UpdateCompetitionMatchTeamResultRequest(
            registrationId = UUID.randomUUID(),
            place = 1,
        )
    }
}