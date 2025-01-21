package de.lambda9.ready2race.backend.app.namedParticipant.control

import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object NamedParticipantRepo {

    fun create(
        record: NamedParticipantRecord
    ): JIO<String> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            insertInto(this)
                .set(record)
                .returningResult(NAME)
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
        name: String,
        f: NamedParticipantRecord.() -> Unit
    ): JIO<Unit> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            selectFrom(this)
                .where(NAME.eq(name))
                .fetchOne()
                ?.apply(f)
                ?.update()
        }
    }

    fun delete(
        name: String,
    ): JIO<Int> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            deleteFrom(this)
                .where(NAME.eq(name))
                .execute()
        }
    }

}