package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class AppUserWithRolesSort: Sortable {
    ID,
    FIRSTNAME,
    LASTNAME,
    EMAIL;

    override fun toFields(): List<Field<*>> =  when (this) {
        ID -> listOf(APP_USER_WITH_ROLES.ID)
        FIRSTNAME -> listOf(APP_USER_WITH_ROLES.FIRSTNAME)
        LASTNAME -> listOf(APP_USER_WITH_ROLES.LASTNAME)
        EMAIL -> listOf(APP_USER_WITH_ROLES.EMAIL)
    }
}