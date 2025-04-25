package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.EVERY_APP_USER_WITH_ROLES
import org.jooq.Field

enum class EveryAppUserWithRolesSort: Sortable {
    ID,
    FIRSTNAME,
    LASTNAME;

    override fun toField(): Field<*> =  when (this) {
        ID -> EVERY_APP_USER_WITH_ROLES.ID
        FIRSTNAME -> EVERY_APP_USER_WITH_ROLES.FIRSTNAME
        LASTNAME -> EVERY_APP_USER_WITH_ROLES.LASTNAME
    }
}