package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern

data class PasswordResetInitRequest(
    val email: String,
    val language: EmailLanguage,
    val callbackUrl: String,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::email validate pattern(emailPattern),
        this::callbackUrl validate notBlank,
    )

    companion object {
        val example
            get() = PasswordResetInitRequest(
                email = "john.doe@example.com",
                language = EmailLanguage.EN,
                callbackUrl = "https://example.com/verifyRegistration",
            )
    }
}