package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserPasswordResetRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_PASSWORD_RESET
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID

object AppUserPasswordResetRepo {

    fun create(
        record: AppUserPasswordResetRecord,
    ): JIO<String> = Jooq.query {
        with(APP_USER_PASSWORD_RESET) {
            insertInto(this)
                .set(record)
                .returningResult(TOKEN)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun consume(
        token: String,
    ): JIO<AppUserPasswordResetRecord?> = Jooq.query {
        with(APP_USER_PASSWORD_RESET) {
            deleteFrom(this)
                .where(TOKEN.eq(token))
                .and(EXPIRES_AT.gt(LocalDateTime.now()))
                .returning()
                .fetchOne()
        }
    }

    fun delete(
        appUserId: UUID,
    ): JIO<Int> = Jooq.query {
        with(APP_USER_PASSWORD_RESET){
            deleteFrom(this)
                .where(APP_USER.eq(appUserId))
                .execute()
        }
    }

    fun deleteExpired(): JIO<Int> = Jooq.query {
        with(APP_USER_PASSWORD_RESET){
            deleteFrom(this)
                .where(EXPIRES_AT.le(LocalDateTime.now()))
                .execute()
        }
    }
}