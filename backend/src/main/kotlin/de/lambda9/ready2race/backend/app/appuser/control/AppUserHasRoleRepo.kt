package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_HAS_ROLE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object AppUserHasRoleRepo {

    fun create(
        userId: UUID,
        roleIds: List<UUID>,
    ): JIO<Int> = Jooq.query {
        batchInsert(
            roleIds.map {
                AppUserHasRoleRecord(
                    appUser = userId,
                    role = it
                )
            }
        ).execute().size
    }

    fun exists(
        userId: UUID,
        roleId: UUID,
    ): JIO<Boolean> = Jooq.query {
        with(APP_USER_HAS_ROLE) {
            fetchExists(
                this,
                DSL.and(
                    APP_USER.eq(userId),
                    ROLE.eq(roleId)
                )
            )
        }
    }
}