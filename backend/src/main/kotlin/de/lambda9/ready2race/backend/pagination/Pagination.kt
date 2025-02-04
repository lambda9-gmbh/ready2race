package de.lambda9.ready2race.backend.pagination

data class Pagination<S: Sortable> (
    val total: Int,
    val limit: Int,
    val offset: Int,
    val sort: List<Order<S>>,
    val search: String?,
)
