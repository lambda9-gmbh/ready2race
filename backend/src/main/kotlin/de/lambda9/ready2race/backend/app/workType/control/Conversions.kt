package de.lambda9.ready2race.backend.app.workType.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeDto
import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeUpsertDto
import de.lambda9.ready2race.backend.database.generated.tables.records.WorkTypeRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun WorkTypeUpsertDto.toRecord(userId: UUID): App<Nothing, WorkTypeRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        WorkTypeRecord(
            id = UUID.randomUUID(),
            name = name,
            description = description,
            minUser = minUser,
            maxUser = maxUser,
            color = color,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)

fun WorkTypeRecord.toDto(): App<Nothing, WorkTypeDto> = KIO.comprehension {
    KIO.ok(
        WorkTypeDto(
            id = id,
            name = name,
            description = description,
            minUser = minUser,
            maxUser = maxUser,
            color = color,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    )
}
