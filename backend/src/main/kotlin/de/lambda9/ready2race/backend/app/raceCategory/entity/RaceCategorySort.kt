package de.lambda9.ready2race.backend.app.raceCategory.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_CATEGORY
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class RaceCategorySort : Sortable {
    ID,
    NAME;

    override fun toField(): Field<*> = when(this) {
        ID -> RACE_CATEGORY.ID
        NAME -> RACE_CATEGORY.NAME
    }
}