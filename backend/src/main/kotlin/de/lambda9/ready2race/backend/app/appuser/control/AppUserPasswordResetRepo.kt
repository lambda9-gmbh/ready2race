package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserPasswordResetRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_PASSWORD_RESET
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID

object AppUserPasswordResetRepo {

    fun create(record: AppUserPasswordResetRecord) = APP_USER_PASSWORD_RESET.insertReturning(record) { TOKEN }

    fun deleteByAppUserId(appUserId: UUID) = APP_USER_PASSWORD_RESET.delete { APP_USER.eq(appUserId) }
    fun deleteExpired() = APP_USER_PASSWORD_RESET.delete { EXPIRES_AT.le(LocalDateTime.now()) }

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
}