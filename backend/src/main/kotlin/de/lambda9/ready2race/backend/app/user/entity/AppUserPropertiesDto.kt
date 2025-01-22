package de.lambda9.ready2race.backend.app.user.entity

import de.lambda9.ready2race.backend.plugins.Validatable
import io.ktor.server.plugins.requestvalidation.*
import java.util.*

data class AppUserPropertiesDto(
    val firstname: String,
    val lastname: String,
    val email: String,
    val roles: List<UUID>,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: test()
}
