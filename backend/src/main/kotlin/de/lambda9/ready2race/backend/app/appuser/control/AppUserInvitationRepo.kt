package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_WITH_ROLES
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime

object AppUserInvitationRepo {

    fun create(
        record: AppUserInvitationRecord,
    ): JIO<String> = Jooq.query {
        with(APP_USER_INVITATION) {
            insertInto(this)
                .set(record)
                .returningResult(TOKEN)
                .fetchOne()!!
                .value1()!!
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