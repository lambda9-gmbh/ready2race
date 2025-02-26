package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserInvitationWithRolesSort
import de.lambda9.ready2race.backend.database.generated.tables.AppUserInvitationWithRoles
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_WITH_ROLES
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.*

object AppUserInvitationRepo {

    private fun AppUserInvitationWithRoles.searchFields() = listOf(FIRSTNAME, LASTNAME, EMAIL)

    fun create(record: AppUserInvitationRecord) = APP_USER_INVITATION.insertReturning(record) { ID }

    fun countWithRoles(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(APP_USER_INVITATION_WITH_ROLES) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun pageWithRoles(
        params: PaginationParameters<AppUserInvitationWithRolesSort>,
    ): JIO<List<AppUserInvitationWithRolesRecord>> = Jooq.query {
        with(APP_USER_INVITATION_WITH_ROLES) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun consumeWithRoles(
        token: String,
    ): JIO<AppUserInvitationWithRolesRecord?> = Jooq.query {
        val result = with(APP_USER_INVITATION_WITH_ROLES) {
            selectFrom(this)
                .where(TOKEN.eq(token))
                .and(EXPIRES_AT.gt(LocalDateTime.now()))
                .fetchOne()
        }

        with(APP_USER_INVITATION) {
            deleteFrom(this)
                .where(TOKEN.eq(token))
                .execute()
        }

        result
    }

    fun deleteExpired(): JIO<Int> = Jooq.query  {
        with(APP_USER_INVITATION) {
            deleteFrom(this)
                .where(EXPIRES_AT.le(LocalDateTime.now()))
                .execute()
        }
    }
}