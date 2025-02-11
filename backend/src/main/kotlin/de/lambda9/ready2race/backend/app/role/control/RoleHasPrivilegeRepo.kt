package de.lambda9.ready2race.backend.app.role.control

import de.lambda9.ready2race.backend.database.generated.tables.records.RoleHasPrivilegeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE_HAS_PRIVILEGE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object RoleHasPrivilegeRepo {

    fun create(
        records: List<RoleHasPrivilegeRecord>,
    ): JIO<Int> = Jooq.query {
        batchInsert(records).execute().sum()
    }

    fun getPrivilegesByRole(
        roleId: UUID,
    ): JIO<List<UUID>> = Jooq.query {
        with(ROLE_HAS_PRIVILEGE) {
            select(PRIVILEGE)
                .from(this)
                .where(ROLE.eq(roleId))
                .fetch { it.value1() }
        }
    }

}