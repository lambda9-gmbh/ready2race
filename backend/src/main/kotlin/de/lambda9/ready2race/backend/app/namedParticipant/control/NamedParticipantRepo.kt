package de.lambda9.ready2race.backend.app.namedParticipant.control

import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object NamedParticipantRepo {

    fun create(
        record: NamedParticipantRecord
    ): JIO<UUID> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun getMany(): JIO<List<NamedParticipantRecord>> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            selectFrom(this)
                .fetch()
        }
    }

    fun update(
        namedParticipantId: UUID,
        f: NamedParticipantRecord.() -> Unit
    ): JIO<Boolean> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            (selectFrom(this)
                .where(ID.eq(namedParticipantId))
                .fetchOne() ?: return@query false)
                .apply(f)
                .update()
        }
        true
    }

    fun delete(
        namedParticipantId: UUID,
    ): JIO<Int> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            deleteFrom(this)
                .where(ID.eq(namedParticipantId))
                .execute()
        }
    }

}