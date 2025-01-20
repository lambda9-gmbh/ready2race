package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventProperties
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.tailwind.core.KIO
import java.util.*

fun EventRequest.record(userId: UUID) = EventRecord(
    id = UUID.randomUUID(),
    name = properties.name,
    description = properties.description,
    location = properties.location,
    registrationAvailableFrom = properties.registrationAvailableFrom,
    registrationAvailableTo = properties.registrationAvailableTo,
    paymentDueDate = properties.paymentDueDate,
    invoicePrefix = properties.invoicePrefix,
    createdBy = userId,
    updatedBy = userId,
)

fun EventRecord.eventDto(): App<Nothing, EventDto> = KIO.ok(
    EventDto(
        id = id!!,
        properties = EventProperties(
            name = name!!,
            description = description,
            location = location,
            registrationAvailableFrom = registrationAvailableFrom,
            registrationAvailableTo = registrationAvailableTo,
            paymentDueDate = paymentDueDate,
            invoicePrefix = invoicePrefix,
        )
    )
)