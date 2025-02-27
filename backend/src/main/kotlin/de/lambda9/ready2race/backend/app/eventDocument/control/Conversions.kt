package de.lambda9.ready2race.backend.app.eventDocument.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.toDto
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentDto
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentViewRecord

fun EventDocumentViewRecord.toDto(): App<Nothing, EventDocumentDto> =
    createdBy.toDto().map {
        EventDocumentDto(
            id = id!!,
            documentType = documentType,
            name = name!!,
            createdAt = createdAt!!,
            createdBy = it
        )
    }