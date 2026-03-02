package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_WITH_MATCHES
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_WITH_TIMESLOTS
import de.lambda9.ready2race.backend.database.select
import java.util.UUID

object EventDayWithMatchesRepo {
    fun selectByEventDayId(eventDayId: UUID) = EVENT_DAY_WITH_MATCHES.select { EVENT_DAY_WITH_MATCHES.ID.eq(eventDayId) }
}