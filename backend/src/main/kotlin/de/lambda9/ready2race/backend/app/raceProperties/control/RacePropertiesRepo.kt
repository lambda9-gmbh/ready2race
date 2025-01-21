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
        raceId: UUID?,
        raceTemplateId: UUID?,
        f: RacePropertiesRecord.() -> Unit
    ): JIO<Unit> = Jooq.query {
        with(RACE_PROPERTIES){
            selectFrom(this)
                .where(if(raceId != null) RACE.eq(raceId) else RACE_TEMPLATE.eq(raceTemplateId))
                .fetchOne()
                ?.apply(f)
                ?.update()
        }
    }

    fun getIdByRaceId(
        raceId: UUID)
    : JIO<UUID?> = Jooq.query {
        with(RACE_PROPERTIES){
            select(ID)
                .from(this)
                .where(RACE.eq(raceId))
                .fetchOneInto(UUID::class.java)
        }
    }
}