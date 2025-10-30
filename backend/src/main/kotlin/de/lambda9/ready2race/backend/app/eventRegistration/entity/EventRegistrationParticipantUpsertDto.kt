package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.BooleanValidators.isFalseOrNull
import de.lambda9.ready2race.backend.validation.validators.BooleanValidators.isTrue
import de.lambda9.ready2race.backend.validation.validators.IntValidators.max
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.time.LocalDateTime
import java.util.*

data class EventRegistrationParticipantUpsertDto (
    val id: UUID,
    val isNew: Boolean?,
    val hasChanged: Boolean?,
    val firstname: String,
    val lastname: String,
    val year: Int,
    val gender: Gender,
    val email: String?,
    val external: Boolean?,
    val externalClubName: String?,
    val competitionsSingle: List<CompetitionRegistrationSingleUpsertDto>?,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::firstname validate notBlank,
            this::lastname validate notBlank,
            this::year validate LocalDateTime.now().year.let { allOf(min(it - 120), max(it)) },
            ValidationResult.oneOf(
                ValidationResult.allOf(
                    this::external validate isTrue,
                    this::externalClubName validate allOf(notNull, notBlank)
                ),
                ValidationResult.allOf(
                    this::external validate isFalseOrNull,
                    this::externalClubName validate isNull,
                ),
            ),
        )
}