package de.lambda9.ready2race.backend.app.namedParticipant.control

import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
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
    ): JIO<NamedParticipantRecord?> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            selectFrom(this)
                .where(ID.eq(namedParticipantId))
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
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

    fun findUnknown(
        namedParticipants: List<UUID>
    ): JIO<List<UUID>> = Jooq.query {
        val found = with(NAMED_PARTICIPANT) {
            select(ID)
                .from(this)
                .where(DSL.or(namedParticipants.map { ID.eq(it) }))
                .fetch { it.value1() }
        }
        namedParticipants.filter { !found.contains(it) }
    }
}