package de.lambda9.ready2race.backend.app.bankAccount.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.PayeeBankAccountRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_TEMPLATE
import de.lambda9.ready2race.backend.database.generated.tables.references.CONTACT_INFORMATION_USAGE
import de.lambda9.ready2race.backend.database.generated.tables.references.PAYEE_BANK_ACCOUNT
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

    fun getAsJson(eventId: UUID) = PAYEE_BANK_ACCOUNT.selectAsJson { EVENT.eq(eventId) }

    fun insertJsonData(data: String) = PAYEE_BANK_ACCOUNT.insertJsonData(data)

}