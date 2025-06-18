package de.lambda9.ready2race.backend.app.bankAccount.entity

import java.util.UUID

data class BankAccountDto(
    val id: UUID,
    val holder: String,
    val iban: String,
    val bic: String,
    val bank: String,
)
