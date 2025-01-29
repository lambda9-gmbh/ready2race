package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import java.util.*


data class AssignRacesToDayRequest(
    val races: List<UUID>
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}