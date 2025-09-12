package de.lambda9.ready2race.backend.app.webDAV.entity

import java.time.LocalDateTime
import java.util.*

data class BankAccountExport(
    val id: UUID,
    val holder: String,
    val iban: String,
    val bic: String,
    val bank: String,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)