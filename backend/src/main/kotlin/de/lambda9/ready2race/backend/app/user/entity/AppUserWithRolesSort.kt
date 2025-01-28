package de.lambda9.ready2race.backend.app.user.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class AppUserWithRolesSort: Sortable {
    ID,
    FIRSTNAME,
    LASTNAME;

    override fun toField(): Field<*> =  when (this) {
        ID -> APP_USER_WITH_ROLES.ID
        FIRSTNAME -> APP_USER_WITH_ROLES.FIRSTNAME
        LASTNAME -> APP_USER_WITH_ROLES.LASTNAME
    }
}