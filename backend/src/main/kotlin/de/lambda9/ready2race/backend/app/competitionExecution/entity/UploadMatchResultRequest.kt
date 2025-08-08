package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

data class UploadMatchResultRequest(
    val config: UUID,
) : Validatable {

    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example get() = UploadMatchResultRequest(config = UUID.randomUUID())

    }
}
