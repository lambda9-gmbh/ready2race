package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserWithRolesSort
import de.lambda9.ready2race.backend.app.appuser.entity.EveryAppUserWithRolesSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.AppUserWithRoles
import de.lambda9.ready2race.backend.database.generated.tables.EveryAppUserWithRoles
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EveryAppUserWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_PRIVILEGES
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.database.generated.tables.references.EVERY_APP_USER_WITH_ROLES
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object AppUserRepo {

    private fun AppUserWithRoles.searchFields() = listOf(FIRSTNAME, LASTNAME, EMAIL)
    private fun EveryAppUserWithRoles.searchFields() = listOf(FIRSTNAME, LASTNAME, EMAIL)

    fun exists(id: UUID) = APP_USER.exists { ID.eq(id) }

    fun create(record: AppUserRecord) = APP_USER.insertReturning(record) { ID }

    fun update(id: UUID, f: AppUserRecord.() -> Unit) = APP_USER.update(f) { ID.eq(id) }

    fun getByEmail(
        email: String,
    ): JIO<AppUserRecord?> = Jooq.query {
        with(APP_USER) {
            selectFrom(this)
                .where(EMAIL.eq(email))
                .fetchOne()
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

    fun getWithRolesIncludingAllAdmins(
        id: UUID,
    ): JIO<EveryAppUserWithRolesRecord?> = Jooq.query {
        with(EVERY_APP_USER_WITH_ROLES) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
        }
    }

    fun getAllByClubIdWithRoles(
        clubId: UUID
    ): JIO<List<AppUserWithRolesRecord>> = Jooq.query {
        with(APP_USER_WITH_ROLES) {
            selectFrom(this)
                .where(CLUB.eq(clubId))
                .fetch()
        }
    }

    fun countWithRoles(
        search: String?,
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

    fun countWithRolesIncludingAdmins(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(EVERY_APP_USER_WITH_ROLES) {
            fetchCount(this, search.metaSearch(searchFields()).and(ID.ne(SYSTEM_USER)))
        }
    }

    fun pageWithRolesIncludingAdmins(
        params: PaginationParameters<EveryAppUserWithRolesSort>
    ): JIO<List<EveryAppUserWithRolesRecord>> = Jooq.query {
        with(EVERY_APP_USER_WITH_ROLES) {
            selectFrom(this)
                .page(params, searchFields()) {
                    ID.ne(SYSTEM_USER)
                }
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

}