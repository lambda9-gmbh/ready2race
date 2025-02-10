package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserSessionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_SESSION
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime

object AppUserSessionRepo {

    fun create(
        record: AppUserSessionRecord,
    ): JIO<String> = Jooq.query {
        with(APP_USER_SESSION) {
            insertInto(this)
                .set(record)
                .returningResult(TOKEN)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun update(
        token: String,
        f: AppUserSessionRecord.() -> Unit
    ): JIO<AppUserSessionRecord?> = Jooq.query {
        with(APP_USER_SESSION) {
            selectFrom(this)
                .where(TOKEN.eq(token))
                .and(EXPIRES_AT.gt(LocalDateTime.now()))
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
    }

    fun delete(
        token: String?,
    ): JIO<Unit> = Jooq.query {
        with(APP_USER_SESSION) {
            deleteFrom(this)
                .where(TOKEN.eq(token))
                .execute()
        }
    }

    fun deleteExpired(): JIO<Int> = Jooq.query {
        with(APP_USER_SESSION) {
            deleteFrom(this)
                .where(EXPIRES_AT.le(LocalDateTime.now()))
                .execute()
        }
    }
}