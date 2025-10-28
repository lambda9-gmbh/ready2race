package de.lambda9.ready2race.backend.app.workType.control

import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.WorkTypeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WORK_TYPE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object WorkTypeRepo {

    private fun searchFields() = listOf(WORK_TYPE.NAME)

    fun create(record: WorkTypeRecord) = WORK_TYPE.insertReturning(record) { ID }

    fun create(records: List<WorkTypeRecord>) = WORK_TYPE.insert(records)

    fun getOverlapIds(ids: List<UUID>) = WORK_TYPE.select({ ID }) { ID.`in`(ids) }

    fun update(id: UUID, f: WorkTypeRecord.() -> Unit) =
        WORK_TYPE.update(f) { ID.eq(id) }

    fun delete(id: UUID) = WORK_TYPE.delete { ID.eq(id) }

    fun all() = WORK_TYPE.select()

    fun countByEvent(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(WORK_TYPE) {
            fetchCount(
                this,
                search.metaSearch(searchFields())

            )
        }
    }

    fun pageByEvent(
        params: PaginationParameters<WorkTypeSort>,
    ): JIO<List<WorkTypeRecord>> = Jooq.query {
        with(WORK_TYPE) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun allAsJson() = WORK_TYPE.selectAsJson()

    fun insertJsonData(data: String) = WORK_TYPE.insertJsonData(data)
}