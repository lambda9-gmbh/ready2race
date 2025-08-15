package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_FOR_EVENT
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class AppUserForEventSort : Sortable {
    FIRSTNAME, LASTNAME, EMAIL;

    override fun toFields(): List<Field<*>> = when (this) {
        FIRSTNAME -> listOf(APP_USER_FOR_EVENT.FIRSTNAME)
        LASTNAME -> listOf(APP_USER_FOR_EVENT.LASTNAME)
        EMAIL -> listOf(APP_USER_FOR_EVENT.EMAIL)
    }
}