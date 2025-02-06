package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import java.util.*

data class RaceRequest (
    val properties: RacePropertiesRequestDto,
    val template: UUID?,
): Validatable {
    override fun validate(): ValidationResult =
        this::properties.validate()

    companion object{
        val example get() = RaceRequest(
            properties = RacePropertiesRequestDto.example,
            template = UUID.randomUUID(),
        )

    }
}