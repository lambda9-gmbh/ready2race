package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import java.util.*

data class RaceRequest (
    val raceProperties: RacePropertiesRequestDto,
    val template: UUID?,
): Validatable {
    override fun validate(): StructuredValidationResult =
        this::raceProperties.validate()
}