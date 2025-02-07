package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime

object AppUserRegistrationRepo {

    fun create(
        record: AppUserRegistrationRecord,
    ): JIO<String> = Jooq.query {
        with(APP_USER_REGISTRATION) {
            insertInto(this)
                .set(record)
                .returningResult(TOKEN)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun consume(
        token: String,
    ): JIO<AppUserRegistrationRecord?> = Jooq.query {
        with(APP_USER_REGISTRATION) {
            deleteFrom(this)
                .where(TOKEN.eq(token))
                .and(EXPIRES_AT.gt(LocalDateTime.now()))
                .returning()
                .fetchOne()
        }
    }

    fun deleteExpired(): JIO<Int> = Jooq.query {
        with(APP_USER_REGISTRATION) {
            deleteFrom(this)
                .where(EXPIRES_AT.le(LocalDateTime.now()))
                .execute()
        }
    }
}