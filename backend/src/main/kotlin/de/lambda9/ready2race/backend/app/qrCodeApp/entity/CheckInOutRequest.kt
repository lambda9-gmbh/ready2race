package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

data class CheckInOutRequest(
    val eventId: UUID
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example = CheckInOutRequest(UUID.randomUUID())
    }
}