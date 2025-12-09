package de.lambda9.ready2race.backend.app.globalConfigurations.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class UpdateGlobalConfigurationsRequest(
    val allowClubCreationOnRegistration: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = UpdateGlobalConfigurationsRequest(
                allowClubCreationOnRegistration = true,
            )
    }
}