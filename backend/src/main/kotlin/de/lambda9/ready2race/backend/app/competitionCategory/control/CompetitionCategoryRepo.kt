package de.lambda9.ready2race.backend.app.competitionCategory.control

import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategorySort
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionCategory
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CATEGORY
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID


object CompetitionCategoryRepo {

    private fun CompetitionCategory.searchFields() = listOf(NAME)

    fun create(
        record: CompetitionCategoryRecord,
    ): JIO<UUID> = Jooq.query {
        with(COMPETITION_CATEGORY){
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun exists(
        id: UUID
    ): JIO<Boolean> = Jooq.query {
        with(COMPETITION_CATEGORY) {
            fetchExists(this, ID.eq(id))
        }
    }

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

    fun update(
        competitionCategoryId: UUID,
        f: CompetitionCategoryRecord.() -> Unit
    ): JIO<CompetitionCategoryRecord?> = Jooq.query {
        with(COMPETITION_CATEGORY) {
            selectFrom(this)
                .where(ID.eq(competitionCategoryId))
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
    }

    fun delete(
        competitionCategoryId: UUID
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_CATEGORY){
            deleteFrom(this)
                .where(ID.eq(competitionCategoryId))
                .execute()
        }
    }
}