package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasCompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_HAS_COMPETITION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object EventDayHasCompetitionRepo {

    fun create(records: List<EventDayHasCompetitionRecord>) = EVENT_DAY_HAS_COMPETITION.insert(records)

    fun deleteByEventDay(
        eventDayId: UUID,
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY_HAS_COMPETITION) {
            deleteFrom(this)
                .where(EVENT_DAY.eq(eventDayId))
                .execute()
        }
    }

    fun deleteByCompetition(
        competitionId: UUID,
    ): JIO<Int> = Jooq.query {
        with(EVENT_DAY_HAS_COMPETITION) {
            deleteFrom(this)
                .where(COMPETITION.eq(competitionId))
                .execute()
        }
    }
}