package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_HAS_ROLE
import de.lambda9.ready2race.backend.database.insert
import org.jooq.impl.DSL
import java.util.*

object AppUserHasRoleRepo {

    fun create(record: AppUserHasRoleRecord) = APP_USER_HAS_ROLE.insert(record)
    fun create(records: Collection<AppUserHasRoleRecord>) = APP_USER_HAS_ROLE.insert(records)

    fun exists(userId: UUID, roleId: UUID) = APP_USER_HAS_ROLE.exists {
        DSL.and(
            APP_USER.eq(userId),
            ROLE.eq(roleId)
        )
    }
}