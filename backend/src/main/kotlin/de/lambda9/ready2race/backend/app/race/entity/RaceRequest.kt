package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validators.Companion.notNull
import java.util.*

data class RaceRequest (
    val properties: RacePropertiesRequestDto?,
    val template: UUID?,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::properties.validate(),
            ValidationResult.oneOf(
                this::properties validate notNull,
                this::template validate notNull,
            )
        )


    companion object{
        val example get() = RaceRequest(
            properties = RacePropertiesRequestDto.example,
            template = null, // todo: should provide 2 examples (one with properties, one with template) or extra details/description
        )
    }
}