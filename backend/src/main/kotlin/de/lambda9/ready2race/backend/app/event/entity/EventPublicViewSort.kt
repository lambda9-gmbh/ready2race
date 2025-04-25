package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_PUBLIC_VIEW
import org.jooq.Field

enum class EventPublicViewSort : Sortable {
    ID,
    NAME,
    EVENT_FROM,
    EVENT_TO;

    override fun toField(): Field<*> = when (this) {
        ID -> EVENT_PUBLIC_VIEW.ID
        NAME -> EVENT_PUBLIC_VIEW.NAME
        EVENT_FROM -> EVENT_PUBLIC_VIEW.EVENT_FROM
        EVENT_TO -> EVENT_PUBLIC_VIEW.EVENT_TO
    }
}