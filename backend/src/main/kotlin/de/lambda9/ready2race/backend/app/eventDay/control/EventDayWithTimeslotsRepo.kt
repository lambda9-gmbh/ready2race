package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_WITH_TIMESLOTS
import de.lambda9.ready2race.backend.database.select
import java.util.UUID

object EventDayWithTimeslotsRepo {
    fun selectByEventId(eventID: UUID) = EVENT_DAY_WITH_TIMESLOTS.select { EVENT_DAY_WITH_TIMESLOTS.EVENT_ID.eq(eventID) }
}