package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasCompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_HAS_COMPETITION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertJsonData
import de.lambda9.ready2race.backend.database.selectAsJson
import java.util.*

object EventDayHasCompetitionRepo {

    fun create(records: List<EventDayHasCompetitionRecord>) = EVENT_DAY_HAS_COMPETITION.insert(records)

    fun deleteByEventDay(eventDayId: UUID) = EVENT_DAY_HAS_COMPETITION.delete { EVENT_DAY.eq(eventDayId) }
    fun deleteByCompetition(competitionId: UUID) = EVENT_DAY_HAS_COMPETITION.delete { COMPETITION.eq(competitionId) }

    fun getAsJson(competitionId: UUID) = EVENT_DAY_HAS_COMPETITION.selectAsJson { COMPETITION.eq(competitionId) }

    fun insertJsonData(data: String) = EVENT_DAY_HAS_COMPETITION.insertJsonData(data)

}