package de.lambda9.ready2race.backend.app.caterer.control

import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionViewSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.CatererTransactionView
import de.lambda9.ready2race.backend.database.generated.tables.records.CatererTransactionViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION
import de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION_VIEW
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object CatererRepo {

    fun CatererTransactionView.searchFields() = listOf(
        CATERER_FIRSTNAME,
        CATERER_LASTNAME,
        USER_FIRSTNAME,
        USER_LASTNAME
    )

    fun create(record: de.lambda9.ready2race.backend.database.generated.tables.records.CatererTransactionRecord): JIO<UUID> =
        CATERER_TRANSACTION.insertReturning(record) { CATERER_TRANSACTION.ID }



    fun countByEventId(
        eventId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(CATERER_TRANSACTION_VIEW) {
            fetchCount(
                this,
                DSL.and(
                    EVENT_ID.eq(eventId),
                    search.metaSearch(searchFields())
                )
            )
        }
    }

    fun pageByEventId(
        eventId: UUID,
        params: PaginationParameters<CatererTransactionViewSort>
    ): JIO<List<CatererTransactionViewRecord>> = Jooq.query {
        with(CATERER_TRANSACTION_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT_ID.eq(eventId)
                }
                .fetch()
        }
    }
}