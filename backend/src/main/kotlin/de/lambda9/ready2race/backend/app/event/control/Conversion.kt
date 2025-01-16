package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import java.util.*

fun EventDto.record(userId: UUID) = EventRecord(
    id = id,
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