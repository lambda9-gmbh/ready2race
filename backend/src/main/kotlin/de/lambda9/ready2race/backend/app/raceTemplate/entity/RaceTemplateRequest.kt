package de.lambda9.ready2race.backend.app.raceTemplate.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable

data class RaceTemplateRequest(
    val raceProperties: RacePropertiesRequestDto
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}