package de.lambda9.ready2race.backend.app.role.control

import de.lambda9.ready2race.backend.app.role.entity.RoleWithPrivilegesSort
import de.lambda9.ready2race.backend.database.generated.tables.RoleWithPrivileges
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE_WITH_PRIVILEGES
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object RoleRepo {

    private fun RoleWithPrivileges.searchFields() = listOf(NAME)

    fun exists(
        id: UUID,
    ): JIO<Boolean> = Jooq.query {
        with(ROLE) {
            fetchExists(this, ID.eq(id))
        }
    }

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

    fun create(
        record: RoleRecord,
    ): JIO<UUID> = Jooq.query {
        insertInto(ROLE).set(record).returningResult(ROLE.ID).fetchOne()!!.value1()!!
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