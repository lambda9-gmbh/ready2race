package de.lambda9.ready2race.backend.app.raceCategory.control

import de.lambda9.ready2race.backend.database.generated.tables.records.RaceCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_CATEGORY
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object RaceCategoryRepo {
    fun create(
        record: RaceCategoryRecord,
    ): JIO<UUID> = Jooq.query {
        with(RACE_CATEGORY){
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun getMany(): JIO<List<RaceCategoryRecord>> = Jooq.query {
        with(RACE_CATEGORY) {
            selectFrom(this)
                .fetch()
        }
    }

    fun update(
        raceCategoryId: UUID,
        f: RaceCategoryRecord.() -> Unit
    ): JIO<Boolean> = Jooq.query {
        with(RACE_CATEGORY) {
            (selectFrom(this)
                .where(ID.eq(raceCategoryId))
                .fetchOne() ?: return@query false)
                .apply(f)
                .update()
        }
        true
    }

    fun delete(
        raceCategoryId: UUID
    ): JIO<Int> = Jooq.query {
        with(RACE_CATEGORY){
            deleteFrom(this)
                .where(ID.eq(raceCategoryId))
                .execute()
        }
    }
}