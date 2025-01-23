package de.lambda9.ready2race.backend.app.user.entity

import de.lambda9.ready2race.backend.plugins.StructuredValidationResult
import de.lambda9.ready2race.backend.plugins.Validatable
import java.util.*

data class AppUserPropertiesDto(
    val firstname: String,
    val lastname: String,
    val email: String,
    val roles: List<UUID>,
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}
