package de.lambda9.ready2race.backend.app.competitionCategory.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CATEGORY
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class CompetitionCategorySort : Sortable {
    ID,
    NAME;

    override fun toField(): Field<*> = when(this) {
        ID -> COMPETITION_CATEGORY.ID
        NAME -> COMPETITION_CATEGORY.NAME
    }
}