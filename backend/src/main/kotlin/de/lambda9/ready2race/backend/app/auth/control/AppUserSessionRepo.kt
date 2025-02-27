package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserSessionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_SESSION
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime

object AppUserSessionRepo {

    fun create(record: AppUserSessionRecord) = APP_USER_SESSION.insertReturning(record) { TOKEN }

    fun update(token: String, f: AppUserSessionRecord.() -> Unit) = APP_USER_SESSION.update(f) {
        DSL.and(
            TOKEN.eq(token),
            EXPIRES_AT.gt(LocalDateTime.now())
        )
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