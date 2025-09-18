package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.PrivilegeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.PRIVILEGE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.parseJsonToRecords
import de.lambda9.ready2race.backend.database.selectAsJson
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object PrivilegeRepo {

    fun create(records: Collection<PrivilegeRecord>) = PRIVILEGE.insert(records)

    fun all(): JIO<List<PrivilegeRecord>> = Jooq.query {
        with(PRIVILEGE) {
            selectFrom(this)
                .fetch()
        }
    }

    fun allAsJson() = PRIVILEGE.selectAsJson()


    fun parseJsonToRecord(data: String) = PRIVILEGE.parseJsonToRecords(data)
}