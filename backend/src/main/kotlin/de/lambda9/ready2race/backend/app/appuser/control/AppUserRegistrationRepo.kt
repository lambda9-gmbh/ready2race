package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.beforeNow
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import kotlin.time.Duration

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

    fun existsByEmail(
        email: String,
    ): JIO<Boolean> = Jooq.query {
        with(APP_USER_REGISTRATION) {
            fetchExists(this, EMAIL.equalIgnoreCase(email))
        }
    }

    fun consume(
        token: String,
        tokenLifeTime: Duration,
    ): JIO<AppUserRegistrationRecord?> = Jooq.query {
        with(APP_USER_REGISTRATION) {
            deleteFrom(this)
                .where(TOKEN.eq(token))
                .and(CREATED_AT.ge(tokenLifeTime.beforeNow()))
                .returning()
                .fetchOne()
        }
    }

    fun deleteExpired(
        tokenLifeTime: Duration,
    ): JIO<Int> = Jooq.query {
        with(APP_USER_REGISTRATION) {
            deleteFrom(this)
                .where(CREATED_AT.lt(tokenLifeTime.beforeNow()))
                .execute()
        }
    }
}