package de.lambda9.ready2race.backend.app.eventDocumentType.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeDto
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentTypeRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun EventDocumentTypeRequest.toRecord(userId: UUID): App<Nothing, EventDocumentTypeRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            EventDocumentTypeRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                required = required,
                confirmationRequired = confirmationRequired,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun EventDocumentTypeRecord.toDto(): App<Nothing, EventDocumentTypeDto> =
    KIO.ok(
        EventDocumentTypeDto(
            id = id,
            name = name,
            description = description,
            required = required,
            confirmationRequired = confirmationRequired,
        )
    )