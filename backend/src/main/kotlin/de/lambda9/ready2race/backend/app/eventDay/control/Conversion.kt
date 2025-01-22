package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayDto
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayProperties
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.tailwind.core.KIO
import java.util.*

fun EventDayRequest.record(userId: UUID, eventId: UUID) = EventDayRecord(
    id = UUID.randomUUID(),
    event = eventId,
    date = properties.date,
    name = properties.name,
    description = properties.description,
    createdBy = userId,
    updatedBy = userId,
)

fun EventDayRecord.eventDayDto(): App<Nothing, EventDayDto> = KIO.ok(
    EventDayDto(
        id = id!!,
        event = event!!,
        properties = EventDayProperties(
            date = date!!,
            name = name,
            description = description,
        )
    )
)