package de.lambda9.ready2race.backend.app.auth.entity

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class LoginRequest(
    val email: String,
    val password: String,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::email validate notBlank,
            this::password validate notBlank,
        )

    companion object {
        val example get() = LoginRequest(
            email = "john.doe@example.com",
            password = "1$=jj9kTp",
        )
    }
}
