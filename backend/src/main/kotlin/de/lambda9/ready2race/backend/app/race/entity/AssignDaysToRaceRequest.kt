package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import java.util.*


data class AssignDaysToRaceRequest(
    val days: List<UUID>
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}