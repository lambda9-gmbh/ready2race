package de.lambda9.ready2race.backend.pagination

import org.jooq.Field

interface Sortable {
    fun toField(): Field<*>
}
