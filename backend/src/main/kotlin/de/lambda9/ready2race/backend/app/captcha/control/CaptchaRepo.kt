package de.lambda9.ready2race.backend.app.captcha.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CaptchaRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CAPTCHA
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.*

object CaptchaRepo {

    fun create(record: CaptchaRecord) = CAPTCHA.insertReturning(record) { ID }

    fun deleteExpired() = CAPTCHA.delete { EXPIRES_AT.le(LocalDateTime.now()) }

    fun consume(
        id: UUID,
    ): JIO<CaptchaRecord?> = Jooq.query {
        with(CAPTCHA) {
            deleteFrom(this)
                .where(ID.eq(id))
                .and(EXPIRES_AT.gt(LocalDateTime.now()))
                .returning()
                .fetchOne()
        }
    }
}