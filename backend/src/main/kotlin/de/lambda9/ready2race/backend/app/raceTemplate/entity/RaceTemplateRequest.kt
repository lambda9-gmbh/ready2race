package de.lambda9.ready2race.backend.app.raceTemplate.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate

data class RaceTemplateRequest(
    val properties: RacePropertiesRequestDto
): Validatable {
    override fun validate(): ValidationResult =
        this::properties.validate()

    companion object{
        val example get() = RaceTemplateRequest(
            properties = RacePropertiesRequestDto.example
        )
    }
}