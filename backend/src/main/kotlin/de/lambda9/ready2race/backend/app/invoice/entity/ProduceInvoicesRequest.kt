package de.lambda9.ready2race.backend.app.invoice.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class ProduceInvoicesRequest(
    val type: RegistrationInvoiceType,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = ProduceInvoicesRequest(
            type = RegistrationInvoiceType.REGULAR,
        )
    }
}
