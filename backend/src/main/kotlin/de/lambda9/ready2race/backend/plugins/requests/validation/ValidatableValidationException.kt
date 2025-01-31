package de.lambda9.ready2race.backend.plugins.requests.validation

import de.lambda9.ready2race.backend.serialization.jsonMapper
import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable

class ValidatableValidationException(
    val value: Validatable,
    val reason: StructuredValidationResult.Invalid,
) : IllegalArgumentException("Validation failed for $value. Reason: ${jsonMapper.writeValueAsString(reason)}")