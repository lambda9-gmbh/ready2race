package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION
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

    fun consume(
        token: String,
    ): JIO<AppUserInvitationRecord?> = Jooq.query {
        with(APP_USER_INVITATION) {
            deleteFrom(this)
                .where(TOKEN.eq(token))
                .and(EXPIRES_AT.gt(LocalDateTime.now()))
                .returning()
                .fetchOne()
        }
    }

    fun deleteExpired(): JIO<Int> = Jooq.query  {
        with(APP_USER_INVITATION) {
            deleteFrom(this)
                .where(EXPIRES_AT.le(LocalDateTime.now()))
                .execute()
        }
    }
}