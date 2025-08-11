package de.lambda9.ready2race.backend.app.appUserWithQrCode.control

import de.lambda9.ready2race.backend.app.appUserWithQrCode.entity.AppUserWithQrCodeSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.AppUserWithQrCodeForEvent
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithQrCodeForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_QR_CODE_FOR_EVENT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object AppUserWithQrCodeRepo {

    private fun AppUserWithQrCodeForEvent.searchFields() = listOf(FIRSTNAME, LASTNAME, EMAIL, QR_CODE_ID)

    fun count(eventId: UUID, search: String?): JIO<Int> = Jooq.query {
        with(APP_USER_WITH_QR_CODE_FOR_EVENT) {
            fetchCount(
                this,
                DSL.and(
                    EVENT_ID.eq(eventId),
                    search.metaSearch(searchFields())
                )
            )
        }
    }

    fun page(
        eventId: UUID,
        params: PaginationParameters<AppUserWithQrCodeSort>
    ): JIO<List<AppUserWithQrCodeForEventRecord>> = Jooq.query {
        with(APP_USER_WITH_QR_CODE_FOR_EVENT) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT_ID.eq(eventId)
                }
                .fetch()
        }
    }
}