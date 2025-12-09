package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserRegistrationSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.AppUserRegistrationView
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION_VIEW
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.*

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

    fun get(token: String): JIO<AppUserRegistrationRecord?> =
        APP_USER_REGISTRATION.selectOne { TOKEN.eq(token).and(EXPIRES_AT.gt(LocalDateTime.now())) }
}