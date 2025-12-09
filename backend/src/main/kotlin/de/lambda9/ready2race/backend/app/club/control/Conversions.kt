package de.lambda9.ready2race.backend.app.club.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.entity.ClubDto
import de.lambda9.ready2race.backend.app.club.entity.ClubSearchDto
import de.lambda9.ready2race.backend.app.club.entity.ClubUpsertDto
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun ClubUpsertDto.toRecord(userId: UUID): App<Nothing, ClubRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            ClubRecord(
                id = UUID.randomUUID(),
                name = this.name,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun ClubRecord.clubDto(): App<Nothing, ClubDto> = KIO.ok(
    ClubDto(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
)

fun ClubRecord.clubSearchDto(): App<Nothing, ClubSearchDto> = KIO.ok(
    ClubSearchDto(
        id = id,
        name = name
    )
)


