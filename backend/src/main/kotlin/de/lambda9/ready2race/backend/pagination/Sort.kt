package de.lambda9.ready2race.backend.pagination

data class Sort<S: Sortable> (
    val orders: List<Order<S>>
)