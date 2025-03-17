package de.lambda9.ready2race.backend.calls.pagination

data class Order<S: Sortable>(
    val field: S,
    val direction: Direction,
)
