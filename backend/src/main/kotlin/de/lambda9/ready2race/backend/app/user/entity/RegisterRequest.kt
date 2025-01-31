package de.lambda9.ready2race.backend.app.user.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.minLength
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import de.lambda9.ready2race.backend.validation.validators.Validators.allOf

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstname: String,
    val lastname: String,
): Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::email validate pattern(emailPattern),
            this::password validate allOf(minLength(10)),
            this::firstname validate notBlank,
            this::lastname validate notBlank,
        )
}
