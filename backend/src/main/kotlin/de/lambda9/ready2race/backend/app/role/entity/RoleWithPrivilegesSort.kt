package de.lambda9.ready2race.backend.app.role.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE_WITH_PRIVILEGES
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class RoleWithPrivilegesSort : Sortable {
    NAME;

    override fun toFields(): List<Field<*>> = when (this) {
        NAME -> listOf(ROLE_WITH_PRIVILEGES.NAME)
    }
}