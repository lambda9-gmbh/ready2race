package de.lambda9.ready2race.backend.http

import org.jooq.Field
import org.jooq.SortField

interface Sortable {
    fun toField(): Field<*>
}

interface LimitedResult<T, S: Sortable> {
    val data: List<T>
    val pagination: Pagination<S>
}


enum class Direction {
    ASC,
    DESC,
}


data class Order<S: Sortable>(
    val field: S,
    val direction: Direction,
)

data class Pagination<S: Sortable> (
    val total: Int,
    val limit: Int,
    val offset: Int,
    val sort: List<Order<S>>,
    val search: String?,
)

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


fun <S: Sortable> List<Order<S>>.toOrderBy(): List<SortField<*>> =
    this.map { it.direction to it.field.toField() }.map { (r, f) ->
        when (r) {
            Direction.DESC -> f.desc()
            Direction.ASC -> f.asc()
        }
    }
