package de.lambda9.ready2race.backend.pagination

data class Order<S: Sortable>(
    val field: S,
    val direction: Direction,
)
