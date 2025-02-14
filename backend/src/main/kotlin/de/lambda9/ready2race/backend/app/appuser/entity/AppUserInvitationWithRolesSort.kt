package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_WITH_ROLES
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class AppUserInvitationWithRolesSort : Sortable {
    EMAIL,
    FIRSTNAME,
    LASTNAME,
    EXPIRES_AT;

    override fun toField(): Field<*> = when (this) {
        EMAIL -> APP_USER_INVITATION_WITH_ROLES.EMAIL
        FIRSTNAME -> APP_USER_INVITATION_WITH_ROLES.FIRSTNAME
        LASTNAME -> APP_USER_INVITATION_WITH_ROLES.LASTNAME
        EXPIRES_AT -> APP_USER_INVITATION_WITH_ROLES.EXPIRES_AT
    }
}