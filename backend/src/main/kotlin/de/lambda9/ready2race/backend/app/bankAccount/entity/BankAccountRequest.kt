package de.lambda9.ready2race.backend.app.bankAccount.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.length
import de.lambda9.ready2race.backend.validation.validators.StringValidators.maxLength
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.oneOf

data class BankAccountRequest(
    val holder: String,
    val iban: String,
    val bic: String,
    val bank: String,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::iban validate maxLength(34),
        this::bic validate oneOf(
            length(8),
            length(11)
        ),
    )

    companion object {
        val example get() = BankAccountRequest(
            holder = "John Doe",
            iban = "DE00000000000000000000",
            bic = "XXXXXXX0000",
            bank = "Name of Bank",
        )
    }
}
