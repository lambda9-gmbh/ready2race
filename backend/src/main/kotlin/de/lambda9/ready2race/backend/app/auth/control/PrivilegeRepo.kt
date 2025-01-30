package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.database.generated.tables.records.PrivilegeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PRIVILEGE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object PrivilegeRepo {

    fun create(
        privileges: List<Privilege>
    ): JIO<Int> = Jooq.query {
        batchInsert(
            privileges.map {
                PrivilegeRecord(
                    id = UUID.randomUUID(),
                    action = it.action.name,
                    resource = it.resource.name,
                    scope = it.scope.name,
                )
            }
        ).execute().sum()
    }

    fun all(): JIO<List<PrivilegeRecord>> = Jooq.query {
        with(PRIVILEGE) {
            selectFrom(this)
                .fetch()
        }
    }
}