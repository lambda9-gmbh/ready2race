package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.BooleanValidators.isFalseOrNull
import de.lambda9.ready2race.backend.validation.validators.BooleanValidators.isTrue
import de.lambda9.ready2race.backend.validation.validators.IntValidators.max
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.isBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.time.LocalDateTime

data class ParticipantUpsertDto(
    val firstname: String,
    val lastname: String,
    val year: Int,
    val gender: Gender,
    val phone: String?,
    val external: Boolean?,
    val externalClubName: String?
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::firstname validate notBlank,
            this::lastname validate notBlank,
            this::year validate LocalDateTime.now().year.let { allOf(min(it - 120), max(it)) },
            this::gender validate notNull,
            ValidationResult.anyOf(
                ValidationResult.allOf(
                    this::external validate isFalseOrNull,
                    this::externalClubName validate isBlank
                ),
                ValidationResult.allOf(
                    this::external validate isTrue,
                    this::externalClubName validate notBlank
                )
            )
        )

    companion object {
        val example
            get() = ParticipantUpsertDto(
                firstname = "Max",
                lastname = "Mustermann",
                year = 1999,
                gender = Gender.M,
                phone = null,
                external = false,
                externalClubName = null,
            )
    }
}