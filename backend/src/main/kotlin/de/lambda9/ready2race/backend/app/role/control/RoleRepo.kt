package de.lambda9.ready2race.backend.app.role.control

import de.lambda9.ready2race.backend.database.generated.tables.Role
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import java.util.UUID

object RoleRepo {

    fun exists(
        condition: Role.() -> Condition,
    ): JIO<Boolean> = Jooq.query {
        fetchExists(ROLE, condition(ROLE))
    }

    fun exists(
        id: UUID,
    ): JIO<Boolean> = exists {
        ID.eq(id)
    }

    fun create(
        record: RoleRecord,
    ): JIO<UUID> = Jooq.query {
        insertInto(ROLE).set(record).returningResult(ROLE.ID).fetchOne()!!.value1()!!
    }
}