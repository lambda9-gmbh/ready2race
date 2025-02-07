package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable

data class VerifyRegistrationRequest(
    val token: String,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.Valid

    companion object {
        val example
            get() = VerifyRegistrationRequest(
                token = "abcde12345...",
            )
    }
}
