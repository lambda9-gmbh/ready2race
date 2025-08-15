package de.lambda9.ready2race.backend.pagination

data class Page<A : Any, S : Sortable>(
    val data: List<A>,
    val pagination: Pagination<S>
)