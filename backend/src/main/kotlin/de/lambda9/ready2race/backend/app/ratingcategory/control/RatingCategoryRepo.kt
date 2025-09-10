package de.lambda9.ready2race.backend.app.ratingcategory.control

import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategorySort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.RatingCategory
import de.lambda9.ready2race.backend.database.generated.tables.records.RatingCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RATING_CATEGORY
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object RatingCategoryRepo {

    private fun RatingCategory.searchFields() = listOf(NAME)

    fun create(record: RatingCategoryRecord) = RATING_CATEGORY.insertReturning(record) { ID }

    fun update(id: UUID, f: RatingCategoryRecord.() -> Unit) = RATING_CATEGORY.update(f) { ID.eq(id) }

    fun delete(id: UUID) = RATING_CATEGORY.delete { ID.eq(id) }

    fun all() = RATING_CATEGORY.select()

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(RATING_CATEGORY) {
            fetchCount(
                this,
                search.metaSearch(searchFields())
            )
        }
    }

    fun page(
        params: PaginationParameters<RatingCategorySort>,
    ): JIO<List<RatingCategoryRecord>> = Jooq.query {
        with(RATING_CATEGORY) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

}