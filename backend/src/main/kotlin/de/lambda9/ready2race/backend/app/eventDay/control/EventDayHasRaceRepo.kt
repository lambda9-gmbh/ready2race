package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_HAS_RACE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object EventDayHasRaceRepo {

    fun createMany(
        records: List<EventDayHasRaceRecord>
    ): JIO<Int> = Jooq.query{
        batchInsert(records)
            .execute()
            .size
    }

    fun deleteManyByEventDay(
        eventDayId: UUID,
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY_HAS_RACE){
            deleteFrom(this)
                .where(EVENT_DAY.eq(eventDayId))
                .execute()
        }
    }

    fun deleteManyByRace(
        raceId: UUID,
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY_HAS_RACE){
            deleteFrom(this)
                .where(RACE.eq(raceId))
                .execute()
        }
    }
}