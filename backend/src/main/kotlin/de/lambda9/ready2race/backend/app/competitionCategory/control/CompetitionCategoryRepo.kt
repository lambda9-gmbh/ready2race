package de.lambda9.ready2race.backend.app.competitionCategory.control

import de.lambda9.ready2race.backend.app.competitionCategory.control.CompetitionCategoryRepo.update
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategorySort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionCategory
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CATEGORY
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID


object CompetitionCategoryRepo {

    private fun CompetitionCategory.searchFields() = listOf(NAME)

    fun create(record: CompetitionCategoryRecord) = COMPETITION_CATEGORY.insertReturning(record) { ID }

    fun exists(id: UUID) = COMPETITION_CATEGORY.exists { ID.eq(id) }

    fun update(id: UUID, f: CompetitionCategoryRecord.() -> Unit) = COMPETITION_CATEGORY.update(f) { ID.eq(id) }

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