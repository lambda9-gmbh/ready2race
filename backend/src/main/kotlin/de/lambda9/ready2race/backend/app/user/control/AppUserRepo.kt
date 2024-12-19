package de.lambda9.ready2race.backend.app.user.control

import de.lambda9.ready2race.backend.app.user.entity.AppUserWithRolesSort
import de.lambda9.ready2race.backend.database.generated.tables.AppUser
import de.lambda9.ready2race.backend.database.generated.tables.AppUserWithRoles
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_PRIVILEGES
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.http.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import java.util.*

object AppUserRepo {

    private fun AppUserWithRoles.searchFields() = listOf(FIRSTNAME, LASTNAME, EMAIL)

    fun exists(
        condition: AppUser.() -> Condition,
    ): JIO<Boolean> = Jooq.query {
        fetchExists(APP_USER, condition(APP_USER))
    }

    fun exists(
        id: UUID,
    ): JIO<Boolean> = exists {
        ID.eq(id)
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
        insertInto(APP_USER).set(record).returningResult(APP_USER.ID).fetchOne()!!.value1()!!
    }
}