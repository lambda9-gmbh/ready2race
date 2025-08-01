package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.database.generated.tables.references.EVERY_APP_USER_WITH_ROLES
import org.jooq.Field

enum class EveryAppUserWithRolesSort: Sortable {
    ID,
    FIRSTNAME,
    LASTNAME,
    EMAIL;

    override fun toFields(): List<Field<*>> =  when (this) {
        ID -> listOf(EVERY_APP_USER_WITH_ROLES.ID)
        FIRSTNAME -> listOf(EVERY_APP_USER_WITH_ROLES.FIRSTNAME)
        LASTNAME -> listOf(EVERY_APP_USER_WITH_ROLES.LASTNAME)
        EMAIL -> listOf(APP_USER_WITH_ROLES.EMAIL)
    }
}