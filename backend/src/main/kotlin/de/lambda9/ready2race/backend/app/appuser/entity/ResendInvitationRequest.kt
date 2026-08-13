package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class ResendInvitationRequest(
    val callbackUrl: String,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::callbackUrl validate notBlank,
        )

    companion object {

        val example get() = ResendInvitationRequest(
            callbackUrl = "https://example.com/acceptInvitation",
        )
    }
}
