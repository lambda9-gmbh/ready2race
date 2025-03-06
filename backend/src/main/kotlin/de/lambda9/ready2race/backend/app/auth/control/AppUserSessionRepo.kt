package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserSessionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_SESSION
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
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

    fun delete(token: String?) = APP_USER_SESSION.delete { TOKEN.eq(token) }
    fun deleteExpired() = APP_USER_SESSION.delete { EXPIRES_AT.le(LocalDateTime.now()) }
}