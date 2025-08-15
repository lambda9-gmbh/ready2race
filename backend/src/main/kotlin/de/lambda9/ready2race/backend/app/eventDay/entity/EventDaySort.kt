package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class EventDaySort : Sortable {
    ID,
    EVENT,
    DATE,
    NAME;

    override fun toFields(): List<Field<*>> = when(this) {
        ID -> listOf(EVENT_DAY.ID)
        EVENT -> listOf(EVENT_DAY.EVENT)
        DATE -> listOf(EVENT_DAY.DATE)
        NAME -> listOf(EVENT_DAY.NAME)
    }
}