package de.lambda9.ready2race.backend.app.workType.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.WORK_TYPE
import org.jooq.Field

enum class WorkTypeSort : Sortable {
    ID,
    NAME,
    CREATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(WORK_TYPE.ID)
        NAME -> listOf(WORK_TYPE.NAME)
        CREATED_AT -> listOf(WORK_TYPE.CREATED_AT)
    }
}