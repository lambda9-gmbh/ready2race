package de.lambda9.ready2race.backend.app.auth.entity

import de.lambda9.ready2race.backend.plugins.*
import de.lambda9.ready2race.backend.plugins.Validators.Companion.allOf
import de.lambda9.ready2race.backend.plugins.Validators.Companion.notBlank

data class LoginRequest(
    val email: String,
    val password: String,
): Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::email.validate { allOf(notBlank, notNull) },
            this::password.validate { notBlank }
        )
}
