package de.lambda9.ready2race.backend.app.caterer.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.math.BigDecimal
import java.util.UUID

data class CatererTransactionRequest(
    val appUserId: UUID,
    val price: BigDecimal,
    val eventId: UUID
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example = CatererTransactionRequest(
            appUserId = UUID.randomUUID(),
            price = BigDecimal("5.50"),
            eventId = UUID.randomUUID()
        )
    }
}