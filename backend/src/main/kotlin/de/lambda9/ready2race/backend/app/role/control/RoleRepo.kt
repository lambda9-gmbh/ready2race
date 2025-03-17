package de.lambda9.ready2race.backend.app.role.control

import de.lambda9.ready2race.backend.app.role.entity.RoleWithPrivilegesSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.RoleWithPrivileges
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE_WITH_PRIVILEGES
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object RoleRepo {

    private fun RoleWithPrivileges.searchFields() = listOf(NAME)

    fun exists(id: UUID) = ROLE.exists { ID.eq(id) }

    fun create(record: RoleRecord) = ROLE.insertReturning(record) { ID }

    fun update(record: RoleRecord, f: RoleRecord.() -> Unit) = ROLE.update(record, f)

    fun get(
        id: UUID,
    ): JIO<RoleRecord?> = Jooq.query {
        with(ROLE) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
        }
    }

    fun getIfExist(
        ids: List<UUID>
    ): JIO<List<RoleRecord>> = Jooq.query {
        with(ROLE) {
            selectFrom(this)
                .where(DSL.or(ids.map { ID.eq(it) }))
                .fetch()
        }
    }

    fun countWithPrivileges(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(ROLE_WITH_PRIVILEGES) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun pageWithPrivileges(
        params: PaginationParameters<RoleWithPrivilegesSort>
    ): JIO<List<RoleWithPrivilegesRecord>> = Jooq.query {
        with(ROLE_WITH_PRIVILEGES) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }
}