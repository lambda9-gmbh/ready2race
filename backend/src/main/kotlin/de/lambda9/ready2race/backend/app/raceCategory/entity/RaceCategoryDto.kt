package de.lambda9.ready2race.backend.app.raceCategory.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class RaceCategoryDto(
    val name: String,
    val description: String?,
): Validatable {
    override fun validate(): StructuredValidationResult =
        this::name validate notBlank

    companion object{
        val example get() = RaceCategoryDto(
            name = "Name",
            description = "Description",
        )
    }
}
