package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserForEventSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.AppUserForEvent
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object AppUserForEventRepo {

    private fun AppUserForEvent.searchFields() = listOf(FIRSTNAME, LASTNAME, EMAIL)

    fun countForEvent(
        eventId: UUID,
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(APP_USER_FOR_EVENT) {
            fetchCount(
                this,
                search.metaSearch(searchFields())
                    .and(EVENT.eq(eventId).or(EVENT.isNull))
            )
        }
    }

    fun pageForEvent(
        eventId: UUID,
        params: PaginationParameters<AppUserForEventSort>,
    ): JIO<List<AppUserForEventRecord>> = Jooq.query {
        with(APP_USER_FOR_EVENT) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId).or(EVENT.isNull)
                }
                .fetch()
        }
    }
}