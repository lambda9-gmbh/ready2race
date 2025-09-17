package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserWithRolesSort
import de.lambda9.ready2race.backend.app.appuser.entity.EveryAppUserWithRolesSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.AppUserWithRoles
import de.lambda9.ready2race.backend.database.generated.tables.EveryAppUserWithRoles
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_PRIVILEGES
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.database.generated.tables.references.EVERY_APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
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
        noClub: Boolean?
    ): JIO<Int> = Jooq.query {

        val noClubCondition = if (noClub == true) {
            APP_USER_WITH_ROLES.CLUB.isNull
        } else DSL.trueCondition()

        with(APP_USER_WITH_ROLES) {
            fetchCount(this, search.metaSearch(searchFields()).and(noClubCondition))
        }
    }

    fun pageWithRoles(
        params: PaginationParameters<AppUserWithRolesSort>,
        noClub: Boolean?
    ): JIO<List<AppUserWithRolesRecord>> = Jooq.query {

        val noClubCondition = if (noClub == true) {
            APP_USER_WITH_ROLES.CLUB.isNull
        } else DSL.trueCondition()

        with(APP_USER_WITH_ROLES) {
            selectFrom(this)
                .page(params, searchFields()) {
                    noClubCondition
                }
                .fetch()
        }
    }

    fun countWithRolesIncludingAdmins(
        search: String?,
        noClub: Boolean?
    ): JIO<Int> = Jooq.query {

        val noClubCondition = if (noClub == true) {
            EVERY_APP_USER_WITH_ROLES.CLUB.isNull
        } else DSL.trueCondition()

        with(EVERY_APP_USER_WITH_ROLES) {
            fetchCount(this, search.metaSearch(searchFields()).and(ID.ne(SYSTEM_USER).and(noClubCondition)))
        }
    }

    fun pageWithRolesIncludingAdmins(
        params: PaginationParameters<EveryAppUserWithRolesSort>,
        noClub: Boolean?
    ): JIO<List<EveryAppUserWithRolesRecord>> = Jooq.query {

        val noClubCondition = if (noClub == true) {
            EVERY_APP_USER_WITH_ROLES.CLUB.isNull
        } else DSL.trueCondition()

        with(EVERY_APP_USER_WITH_ROLES) {
            selectFrom(this)
                .page(params, searchFields()) {
                    ID.ne(SYSTEM_USER).and(noClubCondition)
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

    fun getManyById(
        ids: List<UUID>
    ): JIO<List<AppUserRecord>> = Jooq.query {
        with(APP_USER) {
            selectFrom(this)
                .where(ID.`in`(ids))
                .fetch()
        }
    }

    fun getAllIdsExceptSystemAdmin() = APP_USER.select({ ID }) { ID.ne(SYSTEM_USER) }

    fun getAllExceptSystemAdminAsJson() = APP_USER.selectAsJson { ID.ne(SYSTEM_USER) }

    fun insert(records: List<AppUserRecord>) = APP_USER.insert(records)

    fun getOverlapIds(ids: List<UUID>) = APP_USER.select({ ID }) { ID.`in`(ids) }

    fun getOverlappingEmails(emails: List<String>) = APP_USER.select({ EMAIL }) { EMAIL.`in`(emails) }

    fun parseJsonToRecord(data: String): JIO<List<AppUserRecord>> = Jooq.query {
        fetchFromJSON(data)
            .into(AppUserRecord::class.java)
    }
}