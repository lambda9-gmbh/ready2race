package de.lambda9.ready2race.backend.app.raceTemplate.control

import de.lambda9.ready2race.backend.database.generated.tables.records.RaceTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_TEMPLATE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object RaceTemplateRepo {

    fun create(
        record: RaceTemplateRecord,
    ): JIO<UUID> = Jooq.query {
        with(RACE_TEMPLATE){
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }
}