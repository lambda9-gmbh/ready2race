package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import java.util.UUID

data class InviteRequest(
    val email: String,
    val firstname: String,
    val lastname: String,
    val language: EmailLanguage,
    val roles: List<UUID>,
    val admin: Boolean? = null,
    val callbackUrl: String,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::email validate pattern(emailPattern),
            this::firstname validate notBlank,
            this::lastname validate notBlank,
            this::callbackUrl validate notBlank,
            this::roles validate noDuplicates,
        )

    companion object {
        val example get() = InviteRequest(
            email = "john.doe@example.com",
            firstname = "John",
            lastname = "Doe",
            language = EmailLanguage.EN,
            roles = emptyList(),
            callbackUrl = "https://example.com/acceptInvitation",
        )
    }
}
