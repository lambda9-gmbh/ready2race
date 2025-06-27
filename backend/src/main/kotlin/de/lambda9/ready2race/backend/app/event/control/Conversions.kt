package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventPublicDto
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventPublicViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventViewRecord
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

fun EventViewRecord.eventDto(scope: Privilege.Scope?, userClubId: UUID?): App<Nothing, EventDto> = KIO.ok(
    EventDto(
        id = id!!,
        name = name!!,
        description = description,
        location = location,
        registrationAvailableFrom = registrationAvailableFrom,
        registrationAvailableTo = registrationAvailableTo,
        invoicePrefix = if (scope == Privilege.Scope.GLOBAL) invoicePrefix else null,
        published = published,
        invoicesProduced = if (scope == Privilege.Scope.GLOBAL) invoicesProduced else null,
        paymentDueBy = paymentDueBy,
        registrationCount = if (scope != null) {
            if (scope == Privilege.Scope.GLOBAL) {
                registeredClubs?.size
            } else {
                registeredClubs?.toList()?.filter { it == userClubId }?.size
            }
        } else {
            null
        }
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