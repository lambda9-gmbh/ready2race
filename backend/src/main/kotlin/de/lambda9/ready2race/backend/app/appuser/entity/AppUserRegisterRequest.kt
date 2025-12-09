package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.eventRegistration.entity.ParticipantRegisterCompetitionRequest
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.security.PasswordUtilities.DEFAULT_PASSWORD_MIN_LENGTH
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.isEmpty
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.notEmpty
import de.lambda9.ready2race.backend.validation.validators.IntValidators.max
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.minLength
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.time.LocalDateTime
import java.util.*

data class AppUserRegisterRequest(
    val email: String,
    val password: String,
    val firstname: String,
    val lastname: String,
    val clubId: UUID?,
    val clubname: String?,
    val language: EmailLanguage,
    val callbackUrl: String,
    val registerToSingleCompetitions: List<ParticipantRegisterCompetitionRequest>,
    val birthYear: Int?,
    val gender: Gender?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::email validate pattern(emailPattern),
            this::password validate minLength(DEFAULT_PASSWORD_MIN_LENGTH),
            this::firstname validate notBlank,
            this::lastname validate notBlank,
            this::callbackUrl validate notBlank,
            ValidationResult.oneOf(
                ValidationResult.allOf(
                    this::clubId validate notNull,
                    this::clubname validate isNull,
                ),
                ValidationResult.allOf(
                    this::clubId validate isNull,
                    this::clubname validate notNull,
                )
            ),
            this::birthYear validate LocalDateTime.now().year.let { allOf(min(it - 120), max(it)) },
            this::registerToSingleCompetitions validate collection,
            ValidationResult.oneOf(
                ValidationResult.allOf(
                    this::birthYear validate notNull,
                    this::gender validate notNull,
                    this::registerToSingleCompetitions validate notEmpty
                ),
                ValidationResult.allOf(
                    this::birthYear validate isNull,
                    this::gender validate isNull,
                    this::registerToSingleCompetitions validate isEmpty
                )
            )
        )

    companion object {
        val example
            get() = AppUserRegisterRequest(
                email = "john.doe@example.com",
                password = "5kFlg09?$!dF",
                firstname = "John",
                lastname = "Doe",
                clubId = null,
                clubname = "1.RC Flensburg",
                language = EmailLanguage.EN,
                callbackUrl = "https://example.com/verifyRegistration",
                registerToSingleCompetitions = listOf(ParticipantRegisterCompetitionRequest.example),
                birthYear = 1990,
                gender = Gender.F,
            )
    }
}