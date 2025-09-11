package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_HAS_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE_HAS_PRIVILEGE
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

    // todo: Implementation is prone to errors (ROLE.notIn) - Fix this
    fun deleteExceptSystem(userId: UUID) = APP_USER_HAS_ROLE.delete {
        DSL.and(
            APP_USER.eq(userId),
            ROLE.notIn(ADMIN_ROLE, USER_ROLE, CLUB_REPRESENTATIVE_ROLE)
        )
    }

    fun getByUsers(userIds: List<UUID>) = APP_USER_HAS_ROLE.select{ APP_USER.`in`(userIds) }

    fun getOverlaps(ids: List<Pair<UUID, UUID>>) = APP_USER_HAS_ROLE.select {
        DSL.or(ids.map { (appUser, role) ->
            APP_USER.eq(appUser).and(ROLE.eq(role))
        })
    }
}