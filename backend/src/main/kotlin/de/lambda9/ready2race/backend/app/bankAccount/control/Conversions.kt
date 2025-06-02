package de.lambda9.ready2race.backend.app.bankAccount.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.bankAccount.entity.AssignBankAccountRequest
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountDto
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.BankAccountRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.PayeeBankAccountRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun BankAccountRecord.toDto(): App<Nothing, BankAccountDto> = KIO.ok(
    BankAccountDto(
        id = id,
        holder = holder,
        iban = iban,
        bic = bic,
        bank = bank
    )
)

fun BankAccountRequest.toRecord(userId: UUID): App<Nothing, BankAccountRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        BankAccountRecord(
            id = UUID.randomUUID(),
            holder = holder,
            iban = iban,
            bic = bic,
            bank = bank,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)

/**
 * This conversion assumes `bankAccount` to not be null!
 */

fun AssignBankAccountRequest.toRecord(userId: UUID): App<Nothing, PayeeBankAccountRecord> = KIO.ok(
    PayeeBankAccountRecord(
        bankAccount = bankAccount!!,
        event = event,
        assignedAt = LocalDateTime.now(),
        assignedBy = userId,
    )
)