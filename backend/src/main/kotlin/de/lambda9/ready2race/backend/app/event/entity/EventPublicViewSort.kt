package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_PUBLIC_VIEW
import org.jooq.Field

enum class EventPublicViewSort : Sortable {
    ID,
    NAME,
    EVENT_FROM,
    EVENT_TO;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(EVENT_PUBLIC_VIEW.ID)
        NAME -> listOf(EVENT_PUBLIC_VIEW.NAME)
        EVENT_FROM -> listOf(EVENT_PUBLIC_VIEW.EVENT_FROM)
        EVENT_TO -> listOf(EVENT_PUBLIC_VIEW.EVENT_TO)
    }
}