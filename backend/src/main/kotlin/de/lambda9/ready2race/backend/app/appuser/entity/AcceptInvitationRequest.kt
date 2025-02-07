package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.security.PasswordUtilities.DEFAULT_PASSWORD_MIN_LENGTH
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.minLength

data class AcceptInvitationRequest(
    val token: String,
    val password: String,
): Validatable {
    override fun validate(): ValidationResult =
        this::password validate minLength(DEFAULT_PASSWORD_MIN_LENGTH)

    companion object {
        val example get() = AcceptInvitationRequest(
            token = "abcde12345...",
            password = "5kFlg09?$!dF",
        )
    }
}
