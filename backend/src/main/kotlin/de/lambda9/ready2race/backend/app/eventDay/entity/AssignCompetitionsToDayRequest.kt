package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import java.util.*

data class AssignCompetitionsToDayRequest(
    val competitions: List<UUID>
): Validatable {
    override fun validate(): ValidationResult =
        this::competitions validate noDuplicates

    companion object {
        val example get() = AssignCompetitionsToDayRequest(
            competitions = listOf(
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        )
    }
}