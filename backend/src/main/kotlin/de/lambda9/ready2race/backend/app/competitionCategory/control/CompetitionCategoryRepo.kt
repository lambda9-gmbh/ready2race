package de.lambda9.ready2race.backend.app.competitionCategory.control

import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategorySort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionCategory
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CATEGORY
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*


object CompetitionCategoryRepo {

    private fun CompetitionCategory.searchFields() = listOf(NAME)

    fun create(record: CompetitionCategoryRecord) = COMPETITION_CATEGORY.insertReturning(record) { ID }

    fun create(records: List<CompetitionCategoryRecord>) = COMPETITION_CATEGORY.insert(records)

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_CATEGORY.select({ ID }) { ID.`in`(ids) }

    fun exists(id: UUID) = COMPETITION_CATEGORY.exists { ID.eq(id) }

    fun update(id: UUID, f: CompetitionCategoryRecord.() -> Unit) = COMPETITION_CATEGORY.update(f) { ID.eq(id) }

    fun delete(id: UUID) = COMPETITION_CATEGORY.delete { ID.eq(id) }

    fun all() = COMPETITION_CATEGORY.select()

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_CATEGORY) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<CompetitionCategorySort>
    ): JIO<List<CompetitionCategoryRecord>> = Jooq.query {
        with(COMPETITION_CATEGORY) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun allAsJson() = COMPETITION_CATEGORY.selectAsJson()

    fun insertJsonData(data: String) = COMPETITION_CATEGORY.insertJsonData(data)
}