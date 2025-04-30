package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import org.jooq.Field

enum class EventSort : Sortable {
    ID,
    NAME;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(EVENT.ID)
        NAME -> listOf(EVENT.NAME)
    }
}