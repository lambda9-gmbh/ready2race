package de.lambda9.ready2race.backend.app.task.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.TASK_WITH_RESPONSIBLE_USERS
import org.jooq.Field

enum class TaskWithResponsibleUsersSort : Sortable {
    ID,
    NAME,
    DUE_DATE,
    CREATED_AT,
    STATE;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(TASK_WITH_RESPONSIBLE_USERS.ID)
        NAME -> listOf(TASK_WITH_RESPONSIBLE_USERS.NAME)
        DUE_DATE -> listOf(TASK_WITH_RESPONSIBLE_USERS.DUE_DATE)
        CREATED_AT -> listOf(TASK_WITH_RESPONSIBLE_USERS.CREATED_AT)
        STATE -> listOf(TASK_WITH_RESPONSIBLE_USERS.STATE)
    }
}