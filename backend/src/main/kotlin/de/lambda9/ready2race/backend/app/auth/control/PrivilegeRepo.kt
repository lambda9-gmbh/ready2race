package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.database.generated.tables.records.PrivilegeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PRIVILEGE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object PrivilegeRepo {

    fun create(
        privileges: List<Privilege>
    ): JIO<Int> = Jooq.query {
        batchInsert(
            privileges.map {
                PrivilegeRecord(
                    name = it.name
                )
            }
        ).execute().size
    }

    fun all(): JIO<List<Privilege>> = Jooq.query {
        with(PRIVILEGE) {
            select(NAME)
                .from(this)
                .fetch { Privilege.valueOf(it.value1()!!) }
        }
    }
}