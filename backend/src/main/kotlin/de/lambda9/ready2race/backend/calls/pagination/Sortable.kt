package de.lambda9.ready2race.backend.calls.pagination

import org.jooq.Field

interface Sortable {
    fun toField(): Field<*>
}
