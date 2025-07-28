package de.lambda9.ready2race.backend.app.caterer.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionViewDto
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.CatererTransactionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CatererTransactionViewRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun CatererTransactionRequest.toRecord(catererId: UUID): App<Nothing, CatererTransactionRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            CatererTransactionRecord(
                id = UUID.randomUUID(),
                catererId = catererId,
                appUserId = this.appUserId,
                price = this.price,
                eventId = this.eventId,
                createdAt = now,
                createdBy = catererId,
                updatedAt = now,
                updatedBy = catererId,
            )
        }
    )


fun CatererTransactionViewRecord.toDto():  App<Nothing, CatererTransactionViewDto> = KIO.ok( CatererTransactionViewDto(
    id = id!!,
    catererId = catererId!!,
    catererFirstname = catererFirstname!!,
    catererLastname = catererLastname!!,
    appUserId = appUserId!!,
    userFirstname = userFirstname!!,
    userLastname = userLastname!!,
    eventId = eventId!!,
    price = price!!,
    createdAt = createdAt!!
))