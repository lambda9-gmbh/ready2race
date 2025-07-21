package de.lambda9.ready2race.backend.app.caterer.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.BigDecimalValidators
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.math.BigDecimal
import java.util.UUID

data class NewCatererTransactionDTO(
    val appUserId: UUID,
    val price: BigDecimal?,
    val eventId: UUID
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::appUserId validate notNull,
            this::eventId validate notNull
        )
    
    companion object {
        val example = NewCatererTransactionDTO(
            appUserId = UUID.randomUUID(),
            price = BigDecimal("5.50"),
            eventId = UUID.randomUUID()
        )
    }
}