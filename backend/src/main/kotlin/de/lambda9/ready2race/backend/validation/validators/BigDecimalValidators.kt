package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import java.math.BigDecimal

object BigDecimalValidators {
    val notNegative get() = Validator<BigDecimal?> {
        if (it != null && it < BigDecimal.ZERO) {
            StructuredValidationResult.Invalid.Message { "is negative" }
        } else {
            StructuredValidationResult.Valid
        }
    }
}