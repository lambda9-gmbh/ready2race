package de.lambda9.ready2race.backend.app.competitionTemplate.entity

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequestDto
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate

data class CompetitionTemplateRequest(
    val properties: CompetitionPropertiesRequestDto
): Validatable {
    override fun validate(): ValidationResult =
        this::properties.validate()

    companion object{
        val example get() = CompetitionTemplateRequest(
            properties = CompetitionPropertiesRequestDto.example
        )
    }
}