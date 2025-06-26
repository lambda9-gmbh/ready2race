package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_WITH_ROLES
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import org.jooq.Field

enum class AppUserInvitationWithRolesSort : Sortable {
    EMAIL,
    FIRSTNAME,
    LASTNAME,
    EXPIRES_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        EMAIL -> listOf(APP_USER_INVITATION_WITH_ROLES.EMAIL)
        FIRSTNAME -> listOf(APP_USER_INVITATION_WITH_ROLES.FIRSTNAME)
        LASTNAME -> listOf(APP_USER_INVITATION_WITH_ROLES.LASTNAME)
        EXPIRES_AT -> listOf(APP_USER_INVITATION_WITH_ROLES.EXPIRES_AT)
    }
}