package de.lambda9.ready2race.backend.app.contactInformation.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.contactInformation.entity.AssignContactInformationRequest
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationDto
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.ContactInformationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ContactInformationUsageRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun ContactInformationRequest.toRecord(userId: UUID): App<Nothing, ContactInformationRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            ContactInformationRecord(
                id = UUID.randomUUID(),
                name = name,
                addressZip = addressZip,
                addressCity = addressCity,
                addressStreet = addressStreet,
                email = email,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun ContactInformationRecord.toDto(): App<Nothing, ContactInformationDto> =
    KIO.ok(
        ContactInformationDto(
            id = id,
            name = name,
            addressZip = addressZip,
            addressCity = addressCity,
            addressStreet = addressStreet,
            email = email,
        )
    )

/**
 * This conversion assumes `contact` to not be null!
 */

fun AssignContactInformationRequest.toRecord(userId: UUID): App<Nothing, ContactInformationUsageRecord> = KIO.ok(
    ContactInformationUsageRecord(
        contactInformation = contact!!,
        event = event,
        assignedAt = LocalDateTime.now(),
        assignedBy = userId
    )
)