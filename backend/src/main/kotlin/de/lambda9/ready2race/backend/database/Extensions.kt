package de.lambda9.ready2race.backend.database

import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.pagination.toOrderBy
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.TableImpl

fun Collection<Condition>.and() = DSL.and(*this.toTypedArray())
fun Collection<Condition>.or() = DSL.or(*this.toTypedArray())

fun String?.metaSearch(fields: List<TableField<*, *>>, splitRegex: Regex = Regex("\\s")): Condition =
    this
        ?.takeIf { it.isNotBlank() }
        ?.split(splitRegex)
        ?.takeIf { it.isNotEmpty() }
        ?.map { s -> fields.map { it.cast(String::class.java).containsIgnoreCase(s) }.or() }
        ?.and()
        ?: DSL.trueCondition()

fun <R : Record, S : Sortable> SelectWhereStep<R>.page(
    paginationParameter: PaginationParameters<S>,
    searchFields: List<TableField<*, *>> = emptyList(),
    where: () -> Condition = { DSL.trueCondition() }
): SelectForUpdateStep<R> =
    this
        .where(where())
        .and(paginationParameter.search.metaSearch(searchFields))
        .orderBy(paginationParameter.sort?.toOrderBy())
        .limit(paginationParameter.limit)
        .offset(paginationParameter.offset)

fun <R : Record> TableImpl<R>.insert(
    record: R,
): JIO<Unit> = Jooq.query {
    insertInto(this@insert)
        .set(record)
        .execute()
}

fun <R : TableRecord<R>> TableImpl<R>.insert(
    records: Collection<R>,
): JIO<Int> = Jooq.query {
    batchInsert(records).execute().sum()
}

fun <R : Record> TableImpl<R>.insertReturning(
    record: R,
): JIO<R> = Jooq.query {
    insertInto(this@insertReturning)
        .set(record)
        .returning()
        .fetchOne()!!
}

fun <R : Record, T : TableImpl<R>, A> T.insertReturning(
    record: R,
    returning: T.() -> TableField<R, A>,
): JIO<A & Any> = Jooq.query {
    insertInto(this@insertReturning)
        .set(record)
        .returningResult(returning())
        .fetchOne()!!
        .value1()!!
}

fun <R : UpdatableRecord<R>> TableImpl<R>.update(
    record: R,
    f: R.() -> Unit,
): JIO<R> = Jooq.query {
    record.apply {
        f()
        update()
    }
}

fun <R : UpdatableRecord<R>, T : TableImpl<R>> T.update(
    f: R.() -> Unit,
    condition: T.() -> Condition,
): JIO<R?> = Jooq.query {
    selectFrom(this@update)
        .where(condition())
        .fetchOne()
        ?.let {
            this@update.update(it, f)
        }
}.andThen { it ?: KIO.ok(null) } // TODO: new convenience fn from lib

fun <R : Record, T : TableImpl<R>> T.exists(
    condition: T.() -> Condition
): JIO<Boolean> = Jooq.query {
    fetchExists(this@exists, condition())
}

fun <R : Record, T : TableImpl<R>> T.delete(
    condition: T.() -> Condition
): JIO<Int> = Jooq.query {
    deleteFrom(this@delete)
        .where(condition())
        .execute()
}

fun <R : Record, T : TableImpl<R>> T.findFirstBy(
    condition: T.() -> Condition
): JIO<R?> = Jooq.query {
    fetchOne(this@findFirstBy, condition())
}
