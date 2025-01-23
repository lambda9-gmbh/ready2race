package de.lambda9.ready2race.backend.app.auth.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.Validators.Companion.notBlank
import de.lambda9.ready2race.backend.validation.validate

data class LoginRequest(
    val email: String,
    val password: String,
): Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::email.validate { notBlank },
            this::password.validate { notBlank }
        )
}
