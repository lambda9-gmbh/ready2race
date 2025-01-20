package de.lambda9.ready2race.backend.app.participantCount.control

import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantCountRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_COUNT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object ParticipantCountRepo {

    fun create(
        record: ParticipantCountRecord,
    ): JIO<UUID> = Jooq.query {
        with(PARTICIPANT_COUNT) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun createMany(
        records: List<ParticipantCountRecord>,
    ): JIO<List<UUID>> = Jooq.query {
        with(PARTICIPANT_COUNT) {
            insertInto(this)
                .values(records[0])
                .returningResult(ID)
                .fetchInto(UUID::class.java)
        }
    }
}