package de.lambda9.ready2race.backend.app.namedParticipant.control

import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object NamedParticipantRepo {

    fun create(
        record: NamedParticipantRecord,
    ): JIO<String> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            insertInto(this)
                .set(record)
                .returningResult(NAME)
                .fetchOne()!!
                .value1()!!
        }
    }
}