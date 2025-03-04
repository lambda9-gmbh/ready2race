package de.lambda9.ready2race.backend.app.eventDocument.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.toDto
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentDto
import de.lambda9.ready2race.backend.app.eventDocumentType.control.toDto
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentViewRecord
import de.lambda9.tailwind.core.KIO

fun EventDocumentViewRecord.toDto(): App<Nothing, EventDocumentDto> = KIO.comprehension {
    val documentTypeDto = documentType?.let { !it.toDto() }
    val createdByDto = createdBy?.let { !it.toDto() }
    val updateByDto = updatedBy?.let { !it.toDto() }
    KIO.ok(
        EventDocumentDto(
            id = id!!,
            documentType = documentTypeDto,
            name = name!!,
            createdAt = createdAt!!,
            createdBy = createdByDto,
            updatedAt = updatedAt!!,
            updatedBy = updateByDto,
        )
    )
}
