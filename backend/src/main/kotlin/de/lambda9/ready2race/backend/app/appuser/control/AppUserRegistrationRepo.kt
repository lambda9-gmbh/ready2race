package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserRegistrationSort
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.AppUserRegistrationView
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION_VIEW
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime

object AppUserRegistrationRepo {

    private fun AppUserRegistrationView.searchFields() = listOf(EMAIL, FIRSTNAME, LASTNAME)

    fun create(record: AppUserRegistrationRecord) = APP_USER_REGISTRATION.insertReturning(record) { ID }

    fun deleteExpired() = APP_USER_REGISTRATION.delete { EXPIRES_AT.le(LocalDateTime.now()) }

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(APP_USER_REGISTRATION_VIEW) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<AppUserRegistrationSort>,
    ): JIO<List<AppUserRegistrationViewRecord>> = Jooq.query {
        with(APP_USER_REGISTRATION_VIEW) {
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
}