package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class ClubUpsertDto(
    val name: String,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
        )

    companion object {
        val example
            get() = ClubUpsertDto(
                name = "Name",
            )
    }
}