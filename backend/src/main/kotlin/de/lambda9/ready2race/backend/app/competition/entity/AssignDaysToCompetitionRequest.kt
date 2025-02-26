package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import java.util.UUID

data class AssignDaysToCompetitionRequest(
    val days: List<UUID>
): Validatable {
    override fun validate(): ValidationResult =
        this::days validate noDuplicates

    companion object {
        val example get() = AssignDaysToCompetitionRequest(
            days = listOf(
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        )
    }
}
