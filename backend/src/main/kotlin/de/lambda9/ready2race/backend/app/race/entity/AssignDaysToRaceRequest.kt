package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import java.util.UUID

data class AssignDaysToRaceRequest(
    val days: List<UUID>
): Validatable {
    override fun validate(): ValidationResult =
        this::days validate noDuplicates

    companion object {
        val example get() = AssignDaysToRaceRequest(
            days = listOf(
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        )
    }
}
