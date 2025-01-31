package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import java.util.*

data class AssignRacesToDayRequest(
    val races: List<UUID>
): Validatable {
    override fun validate(): StructuredValidationResult =
        this::races validate noDuplicates

    companion object {
        val example get() = AssignRacesToDayRequest(
            races = listOf(
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        )
    }
}