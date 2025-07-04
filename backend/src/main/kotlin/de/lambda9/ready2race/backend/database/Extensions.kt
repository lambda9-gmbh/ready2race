package de.lambda9.ready2race.backend.database

import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.calls.pagination.toOrderBy
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.TableImpl

fun Collection<Condition>.and() = DSL.and(*this.toTypedArray())
fun Collection<Condition>.or() = DSL.or(*this.toTypedArray())

fun String?.metaSearch(fields: List<Field<*>>, splitRegex: Regex = Regex("\\s")): Condition =
    this
        ?.takeIf { it.isNotBlank() }
        ?.split(splitRegex)
        ?.takeIf { it.isNotEmpty() }
        ?.map { s -> fields.map { it.cast(String::class.java).containsIgnoreCase(s) }.or() }
        ?.and()
        ?: DSL.trueCondition()

fun <R : Record, S : Sortable> SelectWhereStep<R>.page(
    paginationParameter: PaginationParameters<S>,
    searchFields: List<Field<*>> = emptyList(),
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

fun <R : Record, T : TableImpl<R>> T.select(
    condition: T.() -> Condition = { DSL.trueCondition() }
): JIO<List<R>> = Jooq.query {
    fetch(this@select, condition())
}

fun <R : Record, T : TableImpl<R>, A> T.select(
    selection: T.() -> TableField<R, A>,
    condition: T.() -> Condition,
): JIO<List<A>> = Jooq.query {
    select(selection())
        .from(this@select)
        .where(condition())
        .fetch { it.value1() }
}

fun <R : Record, T : TableImpl<R>> T.selectAny(
    condition: T.() -> Condition
): JIO<R?> = Jooq.query {
    fetchAny(this@selectAny, condition())
}

fun <R : Record, T : TableImpl<R>> T.selectOne(
    condition: T.() -> Condition
): JIO<R?> = Jooq.query {
    fetchOne(this@selectOne, condition())
}

fun <R : Record, T : TableImpl<R>, A> T.selectOne(
    selection: T.() -> TableField<R, A>,
    condition: T.() -> Condition
): JIO<A?> = Jooq.query {
    select(selection())
        .from(this@selectOne)
        .where(condition())
        .fetchOne()
        ?.value1()
}

private fun <R : UpdatableRecord<R>> R.updateChanges(
    f: R.() -> Unit,
): R = apply {
    f()
    update()
}

fun <R : UpdatableRecord<R>> TableImpl<R>.update(
    record: R,
    f: R.() -> Unit,
): JIO<R> = Jooq.query {
    record.updateChanges(f)
}

fun <R : UpdatableRecord<R>, T : TableImpl<R>> T.update(
    f: R.() -> Unit,
    condition: T.() -> Condition,
): JIO<R?> = Jooq.query {
    fetchOne(this@update, condition())?.updateChanges(f)
}

fun <R : UpdatableRecord<R>, T : TableImpl<R>> T.updateMany(
    f: R.() -> Unit,
    condition: T.() -> Condition
) = Jooq.query {
    val records = fetch(this@updateMany, condition())
    records.forEach { it.f() }
    batchUpdate(records)
        .execute()
}

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

fun <R : Record, T : TableImpl<R>> T.findOneBy(
    condition: T.() -> Condition
): JIO<R?> = Jooq.query {
    fetchOne(this@findOneBy, condition())
}
