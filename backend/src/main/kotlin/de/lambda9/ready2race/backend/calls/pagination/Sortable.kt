package de.lambda9.ready2race.backend.calls.pagination

import org.jooq.Field

interface Sortable {
    fun toFields(): List<Field<*>>
}
