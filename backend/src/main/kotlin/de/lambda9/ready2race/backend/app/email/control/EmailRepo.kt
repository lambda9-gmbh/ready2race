package de.lambda9.ready2race.backend.app.email.control

import de.lambda9.ready2race.backend.beforeNow
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.DatePart
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration

object EmailRepo {

    fun create(
        record: EmailRecord,
    ): JIO<UUID> = Jooq.query {
        with(EMAIL) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun getAndLockNext(
        retryAfterError: Duration,
    ): JIO<EmailRecord?> = Jooq.query {
        LocalDateTime.now().let { now ->
            with(EMAIL) {
                selectFrom(this)
                    .where(DONT_SEND_BEFORE.le(now))
                    .and(SENT_AT.isNull)
                    .and(
                        DSL.or(
                            LAST_ERROR_AT.isNull,
                            LAST_ERROR_AT.lt(retryAfterError.beforeNow())
                        )
                    )
                    .orderBy(PRIORITY.desc(), CREATED_AT.asc())
                    .forUpdate()
                    .skipLocked()
                    .fetchAny()
            }
        }
    }

    fun update(
        id: UUID,
        f: EmailRecord.() -> Unit
    ): JIO<EmailRecord?> = Jooq.query {
        with(EMAIL) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
    }

    fun deleteSent(): JIO<Int> = Jooq.query {
        with(EMAIL) {
            deleteFrom(this)
                .where(SENT_AT.isNotNull)
                .and(
                    DSL.localDateTimeAdd(
                        SENT_AT,
                        KEEP_AFTER_SENDING,
                        DatePart.SECOND
                    ).lt(LocalDateTime.now())
                )
                .execute()
        }
    }
}