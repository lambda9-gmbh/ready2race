package de.lambda9.ready2race.backend.app.role.control

import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object RoleRepo {

    fun exists(
        id: UUID,
    ): JIO<Boolean> = Jooq.query {
        with(ROLE) {
            fetchExists(this, ID.eq(id))
        }
    }

    fun create(
        record: RoleRecord,
    ): JIO<UUID> = Jooq.query {
        insertInto(ROLE).set(record).returningResult(ROLE.ID).fetchOne()!!.value1()!!
    }
}