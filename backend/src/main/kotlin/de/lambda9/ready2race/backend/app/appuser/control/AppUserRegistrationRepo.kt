package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserRegistrationSort
import de.lambda9.ready2race.backend.database.generated.tables.AppUserRegistration
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime

object AppUserRegistrationRepo {

    private fun AppUserRegistration.searchFields() = listOf(EMAIL, FIRSTNAME, LASTNAME)

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

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(APP_USER_REGISTRATION) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<AppUserRegistrationSort>,
    ): JIO<List<AppUserRegistrationRecord>> = Jooq.query {
        with(APP_USER_REGISTRATION) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
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