package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern

data class InviteRequest(
    val email: String,
    val firstname: String,
    val lastname: String,
    val language: EmailLanguage,
    val callbackUrl: String,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::email validate pattern(emailPattern),
            this::firstname validate notBlank,
            this::lastname validate notBlank,
            this::callbackUrl validate notBlank,
        )

    companion object {
        val example get() = InviteRequest(
            email = "john.doe@example.com",
            firstname = "John",
            lastname = "Doe",
            language = EmailLanguage.EN,
            callbackUrl = "https://example.com/acceptInvitation"
        )
    }
}
