package de.lambda9.ready2race.backend.pagination

data class PaginationParameters<S: Sortable>(
    val limit: Int,
    val offset: Int,
    val sort: List<Order<S>>,
    val search: String?,
) {

    fun toPagination(total: Int) = Pagination(
        total = total,
        limit = limit,
        offset = offset,
        sort = sort,
        search = search,
    )

}
