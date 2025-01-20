package de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.control

import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesHasNamedParticipantRecord
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object RacePropertiesHasNamedParticipantRepo {

    fun createMany(
        records: List<RacePropertiesHasNamedParticipantRecord>
    ): JIO<Int> = Jooq.query {
        batchInsert(records)
            .execute()
            .size
    }

}