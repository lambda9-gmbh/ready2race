package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.BigDecimalValidators
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import java.math.BigDecimal
import java.util.*

data class FeeForCompetitionRequestDto(
    val fee: UUID,
    val required: Boolean,
    val amount: BigDecimal,
    val lateAmount: BigDecimal?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::amount validate amountValidator,
            this::lateAmount validate amountValidator,
        )

    companion object {

        private val amountValidator = allOf(
            BigDecimalValidators.notNegative,
            BigDecimalValidators.currency,
        )

        val example
            get() = FeeForCompetitionRequestDto(
                fee = UUID.randomUUID(),
                required = true,
                amount = BigDecimal("5.50"),
                lateAmount = BigDecimal("11.50"),
            )
    }
}

