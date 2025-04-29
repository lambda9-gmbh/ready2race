package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.util.*

data class UpdateAppUserRequest(
    val firstname: String,
    val lastname: String,
    val roles: List<UUID>,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::firstname validate notBlank,
            this::lastname validate notBlank,
        )

    companion object {
        val example
            get() = UpdateAppUserRequest(
                firstname = "John",
                lastname = "Doe",
                roles = listOf(UUID.randomUUID())
            )
    }
}
