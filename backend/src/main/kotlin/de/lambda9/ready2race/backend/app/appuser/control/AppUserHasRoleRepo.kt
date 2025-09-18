package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_HAS_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE_HAS_PRIVILEGE
import de.lambda9.tailwind.jooq.Jooq
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

    fun getByUsersAsJson(userIds: List<UUID>) = APP_USER_HAS_ROLE.selectAsJson { APP_USER.`in`(userIds) }

    fun insertJsonData(data: String) = APP_USER_HAS_ROLE.insertJsonData(data)
}