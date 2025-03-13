package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import java.math.BigDecimal

object BigDecimalValidators : Validators<BigDecimal?>() {
    val notNegative
        get() = simple("is negative") { it >= BigDecimal.ZERO }

    val currencyScale get() = simple("has scale other than 0 or 2") { it.scale() == 0 || it.scale() == 2 }

    fun exclusiveMax(max: BigDecimal) = simple("is greater than or equal to $max") { it < max }

    val currency get() = allOf(currencyScale, exclusiveMax(BigDecimal(100_000_000)))
}