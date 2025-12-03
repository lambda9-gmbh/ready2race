package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.max
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import java.time.LocalDateTime
import java.util.*

data class ParticipantRegisterRequest(
    val firstname: String,
    val lastname: String,
    val email: String?,
    val clubId: UUID,
    val language: EmailLanguage,
    val registerToSingleCompetitions: List<UUID>,
    val birthYear: Int?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::email validate pattern(emailPattern),
            this::firstname validate notBlank,
            this::lastname validate notBlank,
            this::birthYear validate LocalDateTime.now().year.let { allOf(min(it - 120), max(it)) },
        )

    companion object {
        val example
            get() = ParticipantRegisterRequest(
                email = "john.doe@example.com",
                firstname = "John",
                lastname = "Doe",
                clubId = UUID.randomUUID(),
                language = EmailLanguage.EN,
                registerToSingleCompetitions = listOf(UUID.randomUUID()),
                birthYear = 1990,
            )
    }
}