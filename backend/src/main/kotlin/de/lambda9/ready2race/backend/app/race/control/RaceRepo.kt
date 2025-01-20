package de.lambda9.ready2race.backend.app.race.control

import de.lambda9.ready2race.backend.database.generated.tables.Race
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceWithPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_WITH_PROPERTIES
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object RaceRepo {

    private fun Race.searchFields() = listOf(ID) // todo

    fun create(
        record: RaceRecord,
    ): JIO<UUID> = Jooq.query {
        with(RACE) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun getWithProperties(
        raceId: UUID
    ): JIO<RaceWithPropertiesRecord?> = Jooq.query {
        with(RACE_WITH_PROPERTIES) {
            selectFrom(this)
                .where(ID.eq(raceId))
                .fetchOne()
        }
    }
}