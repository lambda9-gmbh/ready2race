package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION_VIEW
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class AppUserRegistrationSort : Sortable {
    EMAIL,
    FIRSTNAME,
    LASTNAME,
    EXPIRES_AT;

    override fun toField(): Field<*> = when (this) {
        EMAIL -> APP_USER_REGISTRATION_VIEW.EMAIL
        FIRSTNAME -> APP_USER_REGISTRATION_VIEW.FIRSTNAME
        LASTNAME -> APP_USER_REGISTRATION_VIEW.LASTNAME
        EXPIRES_AT -> APP_USER_REGISTRATION_VIEW.EXPIRES_AT
    }
}