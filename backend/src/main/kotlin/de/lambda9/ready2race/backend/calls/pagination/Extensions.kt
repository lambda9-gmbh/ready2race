package de.lambda9.ready2race.backend.calls.pagination

import org.jooq.SortField

fun <S: Sortable> List<Order<S>>.toOrderBy(): List<SortField<*>> =
    this.flatMap { order ->
        order.field.toFields().map {
            when (order.direction) {
                Direction.DESC -> it.desc()
                Direction.ASC -> it.asc()
            }
        }
    }