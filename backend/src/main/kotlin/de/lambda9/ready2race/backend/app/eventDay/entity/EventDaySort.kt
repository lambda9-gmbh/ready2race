package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import org.jooq.Field

enum class EventDaySort : Sortable {
    ID,
    EVENT,
    DATE,
    NAME;

    override fun toField(): Field<*> = when(this) {
        ID -> EVENT_DAY.ID
        EVENT -> EVENT_DAY.EVENT
        DATE -> EVENT_DAY.DATE
        NAME -> EVENT_DAY.NAME
    }
}