package de.lambda9.ready2race.backend.app.competitionCategory.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CATEGORY
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import org.jooq.Field

enum class CompetitionCategorySort : Sortable {
    ID,
    NAME;

    override fun toFields(): List<Field<*>> = when(this) {
        ID -> listOf(COMPETITION_CATEGORY.ID)
        NAME -> listOf(COMPETITION_CATEGORY.NAME)
    }
}