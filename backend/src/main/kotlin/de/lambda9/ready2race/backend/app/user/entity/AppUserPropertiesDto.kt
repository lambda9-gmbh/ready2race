package de.lambda9.ready2race.backend.app.user.entity

import de.lambda9.ready2race.backend.validation.*
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import java.util.*

data class AppUserPropertiesDto(
    val firstname: String,
    val lastname: String,
    val email: String,
    val roles: List<UUID>,
): Validatable {
    override fun validate(): StructuredValidationResult =
        StructuredValidationResult.allOf(
            this::firstname validate notBlank,
            this::lastname validate notBlank,
            this::email validate pattern(emailPattern),
        )
}
