package de.lambda9.ready2race.backend.app.caterer.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransaction
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionViewDto
import de.lambda9.ready2race.backend.app.caterer.entity.NewCatererTransactionDTO
import de.lambda9.ready2race.backend.database.generated.tables.records.CatererTransactionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CatererTransactionViewRecord
import de.lambda9.tailwind.core.KIO
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

fun NewCatererTransactionDTO.toRecord(catererId: UUID): App<Nothing, CatererTransactionRecord> =
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

fun CatererTransactionRecord.toCatererTransaction(): App<Nothing, CatererTransaction> = KIO.ok(
    CatererTransaction(
        id = id,
        catererId = catererId,
        appUserId = appUserId,
        price = price,
        eventId = eventId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = createdBy!!,
    )
)

fun CatererTransactionViewRecord.toDto(): CatererTransactionViewDto = CatererTransactionViewDto(
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
)