package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.database.generated.tables.records.PrivilegeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PRIVILEGE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object PrivilegeRepo {

    fun create(
        records: List<PrivilegeRecord>
    ): JIO<Int> = Jooq.query {
        batchInsert(records).execute().sum()
    }

    fun all(): JIO<List<PrivilegeRecord>> = Jooq.query {
        with(PRIVILEGE) {
            selectFrom(this)
                .fetch()
        }
    }
}