package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventProperties
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun EventRequest.toRecord(userId: UUID): App<Nothing, EventRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            EventRecord(
                id = UUID.randomUUID(),
                name = properties.name,
                description = properties.description,
                location = properties.location,
                registrationAvailableFrom = properties.registrationAvailableFrom,
                registrationAvailableTo = properties.registrationAvailableTo,
                invoicePrefix = properties.invoicePrefix,
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
        properties = EventProperties(
            name = name,
            description = description,
            location = location,
            registrationAvailableFrom = registrationAvailableFrom,
            registrationAvailableTo = registrationAvailableTo,
            invoicePrefix = invoicePrefix,
        )
    )
)