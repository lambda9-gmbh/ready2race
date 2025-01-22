package de.lambda9.ready2race.backend.app.raceProperties.control

import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_PROPERTIES_HAS_NAMED_PARTICIPANT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object RacePropertiesHasNamedParticipantRepo {

    fun createMany(
        records: List<RacePropertiesHasNamedParticipantRecord>
    ): JIO<Int> = Jooq.query {
        batchInsert(records)
            .execute()
            .size
    }

    fun deleteManyByRaceProperties(
        racePropertiesId: UUID,
    ): JIO<Int> = Jooq.query {
        with(RACE_PROPERTIES_HAS_NAMED_PARTICIPANT){
            deleteFrom(this)
                .where(RACE_PROPERTIES.eq(racePropertiesId))
                .execute()
        }
    }

}