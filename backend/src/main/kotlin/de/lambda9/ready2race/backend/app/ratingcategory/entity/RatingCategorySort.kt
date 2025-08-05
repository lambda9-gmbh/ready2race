package de.lambda9.ready2race.backend.app.ratingcategory.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.RATING_CATEGORY
import org.jooq.Field

enum class RatingCategorySort : Sortable {
    NAME;

    override fun toFields(): List<Field<*>> = when (this) {
        NAME -> listOf(RATING_CATEGORY.NAME)
    }
}