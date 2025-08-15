package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_VIEW
import org.jooq.Field

enum class EventViewSort : Sortable {
    ID,
    NAME;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(EVENT_VIEW.ID)
        NAME -> listOf(EVENT_VIEW.NAME)
    }
}