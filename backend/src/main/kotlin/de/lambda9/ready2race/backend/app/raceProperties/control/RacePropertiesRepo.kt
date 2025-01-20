package de.lambda9.ready2race.backend.app.raceProperties.control

import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_PROPERTIES
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object RacePropertiesRepo {

    fun create(
        record: RacePropertiesRecord,
    ): JIO<UUID> = Jooq.query {
        with(RACE_PROPERTIES) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }
}