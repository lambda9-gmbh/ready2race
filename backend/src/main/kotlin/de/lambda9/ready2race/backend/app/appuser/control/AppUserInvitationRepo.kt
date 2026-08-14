package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.afterNow
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserInvitationWithRolesSort
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.AppUserInvitationWithRoles
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_WITH_ROLES
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration

object AppUserInvitationRepo {

    private fun AppUserInvitationWithRoles.searchFields() = listOf(FIRSTNAME, LASTNAME, EMAIL)


    fun create(record: AppUserInvitationRecord) = APP_USER_INVITATION.insertReturning(record) { ID }

    fun delete(id: UUID) = APP_USER_INVITATION.delete { ID.eq(id) }

    fun deleteExpired() = APP_USER_INVITATION.delete { EXPIRES_AT.le(LocalDateTime.now()) }

    /**
     * Gibt der Einladung einen neuen Token und eine volle Laufzeit. Der zuvor verschickte
     * Link wird dadurch ungueltig. Liefert den aktualisierten Datensatz oder null, wenn es
     * die Einladung nicht (mehr) gibt.
     */
    fun refreshToken(
        id: UUID,
        newToken: String,
        lifeTime: Duration,
    ): JIO<AppUserInvitationRecord?> = APP_USER_INVITATION.update({
        token = newToken
        expiresAt = lifeTime.afterNow()
    }) { ID.eq(id) }

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

}