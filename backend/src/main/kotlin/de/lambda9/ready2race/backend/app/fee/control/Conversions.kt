package de.lambda9.ready2race.backend.app.fee.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.fee.entity.FeeDto
import de.lambda9.ready2race.backend.app.fee.entity.FeeRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.FeeRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun FeeRequest.toRecord(userId: UUID): App<Nothing, FeeRecord> =
    KIO.ok(
        LocalDateTime.now().let{ now ->
            FeeRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )
        }
    )

fun FeeRecord.feeDto(): App<Nothing, FeeDto> = KIO.ok(
    FeeDto(
        id = id,
        name = name,
        description = description,
    )
)