package de.lambda9.ready2race.backend.pagination

import org.jooq.Field

interface Sortable {
    fun toFields(): List<Field<*>>
}
