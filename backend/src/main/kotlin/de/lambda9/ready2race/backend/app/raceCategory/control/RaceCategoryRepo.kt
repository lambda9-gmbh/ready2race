package de.lambda9.ready2race.backend.app.raceCategory.control

import de.lambda9.ready2race.backend.database.generated.tables.records.RaceCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_CATEGORY
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object RaceCategoryRepo {
    fun create(
        record: RaceCategoryRecord,
    ): JIO<String> = Jooq.query {
        with(RACE_CATEGORY){
            insertInto(this)
                .set(record)
                .returningResult(NAME)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun getMany(): JIO<List<String>> = Jooq.query {
        with(RACE_CATEGORY) {
            selectFrom(this)
                .fetchInto(String::class.java)
        }
    }

    fun update(
        prevName: String,
        f: RaceCategoryRecord.() -> Unit
    ): JIO<Unit> = Jooq.query {
        with(RACE_CATEGORY) {
            selectFrom(this)
                .where(NAME.eq(prevName))
                .fetchOne()
                ?.apply(f)
                ?.update()
        }
    }

    fun delete(
        name: String
    ): JIO<Int> = Jooq.query {
        with(RACE_CATEGORY){
            deleteFrom(this)
                .where(NAME.eq(name))
                .execute()
        }
    }
}