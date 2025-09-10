package de.lambda9.ready2race.backend.app.fee.control

import de.lambda9.ready2race.backend.app.fee.entity.FeeSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.Fee
import de.lambda9.ready2race.backend.database.generated.tables.records.FeeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.FEE
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object FeeRepo{
    private fun Fee.searchFields() = listOf(NAME)

    fun create(record: FeeRecord) = FEE.insertReturning(record) { ID }

    fun update(id: UUID, f: FeeRecord.() -> Unit) = FEE.update(f) { ID.eq(id) }

    fun delete(id: UUID) = FEE.delete { ID.eq(id) }

    fun all() = FEE.select()

    fun getIfExist(
        ids: List<UUID>,
    ): JIO<List<FeeRecord>> = Jooq.query {
        with(FEE) {
            selectFrom(this)
                .where(DSL.or(ids.map { ID.eq(it) }))
                .fetch()
        }
    }

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(FEE) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<FeeSort>
    ): JIO<List<FeeRecord>> = Jooq.query {
        with(FEE) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }
}