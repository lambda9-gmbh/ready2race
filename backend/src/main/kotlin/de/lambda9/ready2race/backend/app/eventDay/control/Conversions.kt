package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayDto
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun EventDayRequest.toRecord(userId: UUID, eventId: UUID): App<Nothing, EventDayRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            EventDayRecord(
                id = UUID.randomUUID(),
                event = eventId,
                date = date,
                name = name,
                description = description,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun EventDayRecord.eventDayDto(): App<Nothing, EventDayDto> = KIO.ok(
    EventDayDto(
        id = id,
        event = event,
        date = date,
        name = name,
        description = description,
    )
)