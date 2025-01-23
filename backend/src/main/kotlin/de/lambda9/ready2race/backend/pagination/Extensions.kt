package de.lambda9.ready2race.backend.pagination

import org.jooq.SortField

fun <S: Sortable> List<Order<S>>.toOrderBy(): List<SortField<*>> =
    this.map { it.direction to it.field.toField() }.map { (r, f) ->
        when (r) {
            Direction.DESC -> f.desc()
            Direction.ASC -> f.asc()
        }
    }