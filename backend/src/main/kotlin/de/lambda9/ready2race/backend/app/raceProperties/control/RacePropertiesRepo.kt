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

    fun updateByRaceOrTemplate(
        id: UUID,
        f: RacePropertiesRecord.() -> Unit
    ): JIO<Boolean> = Jooq.query {
        with(RACE_PROPERTIES) {
            (selectFrom(this)
                .where(RACE.eq(id).or(RACE_TEMPLATE.eq(id)))
                .fetchOne() ?: return@query false)
                .apply(f)
                .update()
        }
        true
    }

    fun getIdByRaceId(
        raceId: UUID
    )
        : JIO<UUID?> = Jooq.query {
        with(RACE_PROPERTIES) {
            select(ID)
                .from(this)
                .where(RACE.eq(raceId))
                .fetchOneInto(UUID::class.java)
        }
    }
}