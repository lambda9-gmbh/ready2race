package de.lambda9.ready2race.backend.app.bankAccount.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.PayeeBankAccountRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PAYEE_BANK_ACCOUNT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object PayeeBankAccountRepo {

    fun upsert(
        record: PayeeBankAccountRecord,
    ): JIO<PayeeBankAccountRecord> = Jooq.query {
        with(PAYEE_BANK_ACCOUNT) {
            update(this)
                .set(record)
                .where(
                    record.event?.let { EVENT.eq(it) } ?: EVENT.isNull
                )
                .execute()
        }
    }.andThen {
        if (it < 1) {
            PAYEE_BANK_ACCOUNT.insertReturning(record)
        } else {
            KIO.ok(record)
        }
    }

    fun getByEvent(eventId: UUID?) = PAYEE_BANK_ACCOUNT.selectOne { eventId?.let { EVENT.eq(it) } ?: EVENT.isNull }

    fun deleteByEvent(eventId: UUID?) = PAYEE_BANK_ACCOUNT.delete { eventId?.let { EVENT.eq(it) } ?: EVENT.isNull }
    
    fun create(record: PayeeBankAccountRecord) = PAYEE_BANK_ACCOUNT.insert(record)
}