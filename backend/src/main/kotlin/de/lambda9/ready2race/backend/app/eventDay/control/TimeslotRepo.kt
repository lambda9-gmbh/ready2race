package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.EventDay
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object TimeslotRepo {

    fun getByEventDay(eventDayId: UUID) = TIMESLOT.select { EVENT_DAY.eq(eventDayId) }
    fun create(record: TimeslotRecord) = TIMESLOT.insertReturning(record) { ID }
    fun exists(id: UUID) = TIMESLOT.exists { ID.eq(id) }
    fun update(id: UUID, f: TimeslotRecord.() -> Unit) = TIMESLOT.update(f) { ID.eq(id) }
    fun delete(id: UUID) = TIMESLOT.delete { ID.eq(id) }

}