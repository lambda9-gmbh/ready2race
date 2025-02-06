package de.lambda9.ready2race.backend.app.raceTemplate.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate

data class RaceTemplateRequest(
    val raceProperties: RacePropertiesRequestDto
): Validatable {
    override fun validate(): ValidationResult =
        this::raceProperties.validate()

    companion object{
        val example get() = RaceTemplateRequest(
            raceProperties = RacePropertiesRequestDto.example
        )
    }
}