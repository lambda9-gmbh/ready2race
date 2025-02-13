package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserWithRolesSort
import de.lambda9.ready2race.backend.database.generated.tables.AppUserWithRoles
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_PRIVILEGES
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object AppUserRepo {

    private fun AppUserWithRoles.searchFields() = listOf(FIRSTNAME, LASTNAME, EMAIL)

    fun exists(
        id: UUID,
    ): JIO<Boolean> = Jooq.query {
        with(APP_USER) {
            fetchExists(this, ID.eq(id))
        }
    }

    fun getWithRoles(
        id: UUID,
    ): JIO<AppUserWithRolesRecord?> = Jooq.query {
        with(APP_USER_WITH_ROLES) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
        }
    }

    fun countWithRoles(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(APP_USER_WITH_ROLES) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun pageWithRoles(
        params: PaginationParameters<AppUserWithRolesSort>
    ): JIO<List<AppUserWithRolesRecord>> = Jooq.query {
        with(APP_USER_WITH_ROLES) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun getWithPrivileges(
        id: UUID
    ): JIO<AppUserWithPrivilegesRecord?> = Jooq.query {
        with(APP_USER_WITH_PRIVILEGES) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
        }
    }

    fun getWithPrivilegesByEmail(
        email: String
    ): JIO<AppUserWithPrivilegesRecord?> = Jooq.query {
        with(APP_USER_WITH_PRIVILEGES) {
            selectFrom(this)
                .where(EMAIL.eq(email))
                .fetchOne()
        }
    }

    fun create(
        record: AppUserRecord,
    ): JIO<UUID> = Jooq.query {
        with(APP_USER) {
            insertInto(this).set(record).returningResult(ID).fetchOne()!!.value1()!!
        }
    }

    fun update(
        id: UUID,
        f: AppUserRecord.() -> Unit,
    ): JIO<AppUserRecord?> = Jooq.query {
        with(APP_USER) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
    }
}