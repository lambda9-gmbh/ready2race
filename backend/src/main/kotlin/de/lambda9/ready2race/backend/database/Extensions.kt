package de.lambda9.ready2race.backend.database

import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.pagination.toOrderBy
import org.jooq.*
import org.jooq.impl.DSL

fun Collection<Condition>.and() = DSL.and(*this.toTypedArray())
fun Collection<Condition>.or() = DSL.or(*this.toTypedArray())

fun String?.metaSearch(fields: List<TableField<*, *>>, splitRegex: Regex = Regex("\\s")): Condition =
    this
        ?.takeIf { it.isNotBlank() }
        ?.split(splitRegex)
        ?.takeIf { it.isNotEmpty() }
        ?.map { s -> fields.map { it.cast(String::class.java).containsIgnoreCase(s) }.or()}
        ?.and()
        ?: DSL.trueCondition()

fun <R : Record, S : Sortable> SelectWhereStep<R>.page(
    paginationParameter: PaginationParameters<S>,
    searchFields: List<TableField<*,*>> = emptyList(),
    where: () -> Condition = { DSL.trueCondition() }
): SelectForUpdateStep<R> =
    this
        .where(where())
        .and(paginationParameter.search.metaSearch(searchFields))
        .orderBy(paginationParameter.sort.toOrderBy())
        .limit(paginationParameter.limit)
        .offset(paginationParameter.offset)