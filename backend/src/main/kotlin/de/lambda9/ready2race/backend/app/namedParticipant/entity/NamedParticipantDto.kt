package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.plugins.Validatable
import io.ktor.server.plugins.requestvalidation.*

data class NamedParticipantDto(
    val name: String,
    val description: String?,
    val required: Boolean,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: test()
}