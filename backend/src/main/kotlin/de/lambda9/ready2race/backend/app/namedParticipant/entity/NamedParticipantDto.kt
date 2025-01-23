package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.plugins.StructuredValidationResult
import de.lambda9.ready2race.backend.plugins.Validatable

data class NamedParticipantDto(
    val name: String,
    val description: String?,
): Validatable {
    override fun validate(): StructuredValidationResult = StructuredValidationResult.Valid
}