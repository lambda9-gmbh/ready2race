package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionForExportDto
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventForExportDto
import de.lambda9.ready2race.backend.app.event.entity.EventPublicDto
import de.lambda9.ready2race.backend.database.generated.tables.records.EventForExportRecord
import de.lambda9.ready2race.backend.app.event.entity.CreateEventRequest
import de.lambda9.ready2race.backend.app.event.entity.MatchResultType
import de.lambda9.ready2race.backend.database.generated.tables.records.EventPublicViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventViewRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun CreateEventRequest.toRecord(userId: UUID): App<Nothing, EventRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            EventRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                location = location,
                registrationAvailableFrom = registrationAvailableFrom,
                registrationAvailableTo = registrationAvailableTo,
                lateRegistrationAvailableTo = lateRegistrationAvailableTo,
                invoicePrefix = invoicePrefix,
                published = published,
                paymentDueBy = paymentDueBy,
                latePaymentDueBy = latePaymentDueBy,
                mixedTeamTerm = mixedTeamTerm,
                challengeEvent = challengeEvent,
                challengeMatchResultType = challengeResultType?.name,
                selfSubmission = allowSelfSubmission,
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
        lateRegistrationAvailableTo = lateRegistrationAvailableTo,
        invoicePrefix = invoicePrefix.takeIf { scope == Privilege.Scope.GLOBAL },
        published = published,
        invoicesProduced = invoicesProduced.takeIf { scope == Privilege.Scope.GLOBAL },
        lateInvoicesProduced = lateInvoicesProduced.takeIf { scope == Privilege.Scope.GLOBAL },
        paymentDueBy = paymentDueBy,
        latePaymentDueBy = latePaymentDueBy,
        registrationCount = when (scope) {
            Privilege.Scope.OWN -> registeredClubs?.count { it == userClubId }
            Privilege.Scope.GLOBAL -> registeredClubs?.size
            null -> null
        },
        registrationsFinalized = registrationsFinalized!!,
        mixedTeamTerm = mixedTeamTerm,
        challengeEvent = challengeEvent!!,
        challengeResultType = challengeMatchResultType?.let { MatchResultType.valueOf(it) },
        allowSelfSubmission = selfSubmission!!,
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
        lateRegistrationAvailableTo = lateRegistrationAvailableTo,
        createdAt = createdAt!!,
        competitionCount = competitionCount!!,
        eventFrom = eventFrom,
        eventTo = eventTo,
        challengeEvent = challengeEvent!!,
        challengeResultType = challengeMatchResultType?.let { MatchResultType.valueOf(it) },
        allowSelfSubmission = selfSubmission!!,
    )
)

fun EventForExportRecord.toDto(): App<Nothing, EventForExportDto> = KIO.ok(
    EventForExportDto(
        id = id!!,
        name = name!!,
        competitions = competitions!!.map { competition ->
            CompetitionForExportDto(
                id = competition!!.id!!,
                identifier = competition.identifier!!,
                name = competition.name!!,
            )
        }
    )
)