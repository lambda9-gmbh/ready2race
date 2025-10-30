package de.lambda9.ready2race.backend.app.eventParticipant.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class ResendAccessTokenRequest(
    val callbackUrl: String,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example get() = ResendAccessTokenRequest(
            callbackUrl = "https://ready2race.info/challenge/",
        )
    }
}
