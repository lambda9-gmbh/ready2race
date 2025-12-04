package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.notEmpty
import de.lambda9.ready2race.backend.validation.validators.IntValidators.max
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import java.time.LocalDateTime
import java.util.*

data class ParticipantRegisterRequest(
    val firstname: String,
    val lastname: String,
    val gender: Gender,
    val birthYear: Int,
    val email: String?,
    val clubId: UUID,
    val language: EmailLanguage,
    val registerToSingleCompetitions: List<ParticipantRegisterCompetitionRequest>,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::email validate pattern(emailPattern),
            this::firstname validate notBlank,
            this::lastname validate notBlank,
            this::birthYear validate LocalDateTime.now().year.let { allOf(min(it - 120), max(it)) },
            this::registerToSingleCompetitions validate notEmpty,
            this::registerToSingleCompetitions validate collection,
        )

    companion object {
        val example
            get() = ParticipantRegisterRequest(
                firstname = "John",
                lastname = "Doe",
                gender = Gender.F,
                birthYear = 1990,
                email = "john.doe@example.com",
                clubId = UUID.randomUUID(),
                language = EmailLanguage.EN,
                registerToSingleCompetitions = listOf(ParticipantRegisterCompetitionRequest.example),
            )
    }
}