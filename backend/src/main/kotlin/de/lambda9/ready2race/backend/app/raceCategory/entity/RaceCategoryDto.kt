package de.lambda9.ready2race.backend.app.raceCategory.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.maxLength
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validators.allOf

data class RaceCategoryDto(
    val name: String,
    val description: String?,
): Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::name validate allOf(notBlank, maxLength(100)),
            this::description validate notBlank
        )

    companion object{
        val example get() = RaceCategoryDto(
            name = "Name",
            description = "Description",
        )
    }
}