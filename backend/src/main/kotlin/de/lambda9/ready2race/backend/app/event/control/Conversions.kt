package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventPublicDto
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventPublicViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun EventRequest.toRecord(userId: UUID): App<Nothing, EventRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            EventRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                location = location,
                registrationAvailableFrom = registrationAvailableFrom,
                registrationAvailableTo = registrationAvailableTo,
                invoicePrefix = invoicePrefix,
                published = published,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun EventRecord.eventDto(): App<Nothing, EventDto> = KIO.ok(
    EventDto(
        id = id,
        name = name,
        description = description,
        location = location,
        registrationAvailableFrom = registrationAvailableFrom,
        registrationAvailableTo = registrationAvailableTo,
        invoicePrefix = invoicePrefix,
        published = published
    )
)

fun EventPublicViewRecord.eventPublicDto(): App<Nothing, EventPublicDto> = KIO.ok(
    EventPublicDto(
        id = id!!,
        name = name!!,
        description = description,
        location = location,
        registrationAvailableFrom = registrationAvailableFrom,
        registrationAvailableTo = registrationAvailableTo,
        createdAt = createdAt!!,
        competitionCount = competitionCount!!,
        eventFrom = eventFrom,
        eventTo = eventTo

    )
)