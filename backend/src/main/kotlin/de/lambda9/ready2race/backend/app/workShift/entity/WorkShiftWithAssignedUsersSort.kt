package de.lambda9.ready2race.backend.app.workShift.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.WORK_SHIFT_WITH_ASSIGNED_USERS
import org.jooq.Field

enum class WorkShiftWithAssignedUsersSort : Sortable {
    ID,
    EVENT,
    WORK_TYPE_NAME,
    TIME_FROM,
    TIME_TO,
    CREATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(WORK_SHIFT_WITH_ASSIGNED_USERS.ID)
        EVENT -> listOf(WORK_SHIFT_WITH_ASSIGNED_USERS.EVENT)
        WORK_TYPE_NAME -> listOf(WORK_SHIFT_WITH_ASSIGNED_USERS.WORK_TYPE_NAME)
        TIME_FROM -> listOf(WORK_SHIFT_WITH_ASSIGNED_USERS.TIME_FROM)
        TIME_TO -> listOf(WORK_SHIFT_WITH_ASSIGNED_USERS.TIME_TO)
        CREATED_AT -> listOf(WORK_SHIFT_WITH_ASSIGNED_USERS.CREATED_AT)
    }
}