package de.lambda9.ready2race.backend.app.contactInformation.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.ContactInformationUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CONTACT_INFORMATION_USAGE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object ContactInformationUsageRepo {

    fun upsert(
        record: ContactInformationUsageRecord,
    ): JIO<ContactInformationUsageRecord> = Jooq.query {
        with(CONTACT_INFORMATION_USAGE) {
            update(this)
                .set(record)
                .where(record.event?.let { EVENT.eq(it) } ?: EVENT.isNull)
                .execute()
        }
    }.andThen {
        if (it < 1) {
            CONTACT_INFORMATION_USAGE.insertReturning(record)
        } else {
            KIO.ok(record)
        }
    }

    fun getByEvent(eventId: UUID?) =
        CONTACT_INFORMATION_USAGE.selectOne { eventId?.let { EVENT.eq(it) } ?: EVENT.isNull }

    fun deleteByEvent(eventId: UUID?) =
        CONTACT_INFORMATION_USAGE.delete { eventId?.let { EVENT.eq(it) } ?: EVENT.isNull }

    fun create(record: ContactInformationUsageRecord) = CONTACT_INFORMATION_USAGE.insert(record)
}