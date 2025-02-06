package de.lambda9.ready2race.backend.app.raceCategory.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class RaceCategoryDto(
    val name: String,
    val description: String?,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
            this::description validate notBlank
        )

    companion object{
        val example get() = RaceCategoryDto(
            name = "Name",
            description = "Description",
        )
    }
}