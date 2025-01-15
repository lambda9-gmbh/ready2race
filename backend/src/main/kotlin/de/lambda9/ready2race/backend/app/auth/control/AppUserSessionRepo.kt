package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.beforeNow
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserSessionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_SESSION
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration

object AppUserSessionRepo {

    fun create(
        appUserId: UUID,
        token: String,
    ): JIO<Unit> = Jooq.query {
        with(APP_USER_SESSION) {
            LocalDateTime.now().let { now ->
                insertInto(this)
                    .set(APP_USER, appUserId)
                    .set(TOKEN, token)
                    .set(LAST_USED, now)
                    .set(CREATED_AT, now)
                    .execute()
            }
        }
    }

    fun useAndGet(
        token: String,
        tokenLifetime: Duration,
    ): JIO<AppUserSessionRecord?> = Jooq.query {
        with(APP_USER_SESSION) {
            update(this)
                .set(LAST_USED, LocalDateTime.now())
                .where(TOKEN.equalIgnoreCase(token))
                .and(LAST_USED.ge(tokenLifetime.beforeNow()))
                .returning()
                .fetchOne()
        }
    }

    fun delete(
        token: String?
    ): JIO<Unit> = Jooq.query {
        with(APP_USER_SESSION) {
            deleteFrom(this)
                .where(TOKEN.eq(token))
                .execute()
        }
    }
}