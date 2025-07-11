package de.lambda9.ready2race.backend.app.invoice.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class InvoiceUpdateRequestDto(
    val paid: Boolean,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example get() = InvoiceUpdateRequestDto(
            paid = true,
        )

    }
}
