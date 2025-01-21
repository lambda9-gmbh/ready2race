package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.plugins.requestvalidation.*

data class NamedParticipantDto( // todo: Should NamedParticipant have created_by/at and updated_by/at
    val name: String,
    val description: String?,
    val required: Boolean,
): Validatable { // todo: This serves as a Request as well as Response. Should there exist an explicit NamedParticipantRequest?
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: test()
}