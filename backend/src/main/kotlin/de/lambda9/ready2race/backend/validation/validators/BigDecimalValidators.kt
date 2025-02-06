package de.lambda9.ready2race.backend.validation.validators

import java.math.BigDecimal

object BigDecimalValidators : Validators<BigDecimal?>() {
    val notNegative
        get() = simple("is negative") { it >= BigDecimal.ZERO }
}