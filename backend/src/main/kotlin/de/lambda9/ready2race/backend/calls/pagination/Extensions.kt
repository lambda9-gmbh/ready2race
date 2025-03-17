package de.lambda9.ready2race.backend.calls.pagination

import org.jooq.SortField

fun <S: Sortable> List<Order<S>>.toOrderBy(): List<SortField<*>> =
    this.map { order ->
        order.field.toField().let {
            when (order.direction) {
                Direction.DESC -> it.desc()
                Direction.ASC -> it.asc()
            }
        }
    }