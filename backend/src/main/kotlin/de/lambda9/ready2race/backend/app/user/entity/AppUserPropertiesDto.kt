package de.lambda9.ready2race.backend.app.user.entity

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import java.util.*

data class AppUserPropertiesDto(
    val firstname: String,
    val lastname: String,
    val email: String,
    val roles: List<UUID>,
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}
