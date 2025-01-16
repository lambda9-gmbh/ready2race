package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object EventRepo {

    fun create(
        record: EventRecord,
    ): JIO<UUID> = Jooq.query {
        with(EVENT) {
            insertInto(this).set(record).returningResult(ID).fetchOne()!!.value1()!!
        }
    }

    fun getEvent(
        id: UUID
    ): JIO<EventRecord?> = Jooq.query {
        with(EVENT) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
        }
    }
}