package de.lambda9.ready2race.backend.app.bankAccount.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

data class AssignBankAccountRequest(
    val bankAccount: UUID?,
    val event: UUID?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = AssignBankAccountRequest(
            bankAccount = UUID.randomUUID(),
            event = UUID.randomUUID(),
        )
    }
}
