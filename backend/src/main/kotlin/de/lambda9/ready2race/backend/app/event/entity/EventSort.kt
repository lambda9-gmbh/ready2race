package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class EventSort : Sortable {
    ID,
    NAME;

    override fun toField(): Field<*> = when (this) {
        ID -> EVENT.ID
        NAME -> EVENT.NAME
    }
}