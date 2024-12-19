package de.lambda9.ready2race.backend.app.user.entity

import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.plugins.requestvalidation.*
import java.util.*

data class AppUserProperties(
    val firstname: String,
    val lastname: String,
    val email: String,
    val roles: List<UUID>,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid
}
