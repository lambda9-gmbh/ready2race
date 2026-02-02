package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.TIMESLOT
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class TimeslotSort: Sortable {
    ID,
    EVENT_DAY,
    NAME,
    DESCRIPTION,
    START_TIME,
    END_TIME;

    override fun toFields(): List<Field<*>> = when(this) {
        ID -> listOf(TIMESLOT.ID)
        EVENT_DAY -> listOf(TIMESLOT.EVENT_DAY)
        NAME -> listOf(TIMESLOT.NAME)
        DESCRIPTION -> listOf(TIMESLOT.DESCRIPTION)
        START_TIME -> listOf(TIMESLOT.START_TIME)
        END_TIME -> listOf(TIMESLOT.END_TIME)
    }
}