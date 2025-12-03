package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.control.*
import de.lambda9.ready2race.backend.app.event.entity.*
import de.lambda9.ready2race.backend.app.eventRegistration.entity.OpenForRegistrationType
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.kio.discard
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.*
import java.time.LocalDateTime
import java.util.*

object EventService {

    fun addEvent(
        request: CreateEventRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        EventRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun page(
        params: PaginationParameters<EventViewSort>,
        scope: Privilege.Scope?,
        user: AppUserWithPrivilegesRecord?
    ): App<Nothing, ApiResponse.Page<EventDto, EventViewSort>> = KIO.comprehension {
        val total = !EventRepo.count(params.search, scope).orDie()
        val page = !EventRepo.page(params, scope).orDie()

        page.traverse { it.eventDto(scope, user?.club) }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun pagePublicView(
        params: PaginationParameters<EventPublicViewSort>,
    ): App<Nothing, ApiResponse.Page<EventPublicDto, EventPublicViewSort>> = KIO.comprehension {
        val total = !EventRepo.countForPublicView(params.search).orDie()
        val page = !EventRepo.pageForPublicView(params).orDie()

        page.traverse { it.eventPublicDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEvent(
        id: UUID,
        scope: Privilege.Scope?,
        user: AppUserWithPrivilegesRecord?
    ): App<EventError, ApiResponse.Dto<EventDto>> = KIO.comprehension {
        val event = !EventRepo.getScoped(id, scope).orDie().onNullFail { EventError.NotFound }
        event.eventDto(scope, user?.club).map { ApiResponse.Dto(it) }
    }

    // challenge_event can not be updated - this would have too many consequences for an event
    fun updateEvent(
        request: UpdateEventRequest,
        userId: UUID,
        eventId: UUID,
    ): App<EventError, ApiResponse.NoData> = KIO.comprehension {

        val eventRecord = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

        !KIO.failOn(eventRecord.challengeEvent == false && request.challengeResultType != null) { EventError.ChallengeResultTypeNotAllowed }
        !KIO.failOn(eventRecord.challengeEvent == true && request.challengeResultType == null) { EventError.NoChallengeResultTypeProvided }

        !EventRepo.update(eventRecord) {
            name = request.name
            description = request.description
            location = request.location
            registrationAvailableFrom = request.registrationAvailableFrom
            registrationAvailableTo = request.registrationAvailableTo
            lateRegistrationAvailableTo = request.lateRegistrationAvailableTo
            invoicePrefix = request.invoicePrefix
            published = request.published
            paymentDueBy = request.paymentDueBy
            latePaymentDueBy = request.latePaymentDueBy
            mixedTeamTerm = request.mixedTeamTerm
            challengeMatchResultType = request.challengeResultType?.name
            selfSubmission = request.allowSelfSubmission
            submissionNeedsVerification = request.submissionNeedsVerification
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        KIO.ok(ApiResponse.NoData)
    }

    fun deleteEvent(
        id: UUID,
    ): App<EventError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !EventRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(EventError.NotFound)
        } else {
            noData
        }
    }

    fun checkEventExisting(
        eventId: UUID,
    ): App<EventError, Unit> = EventRepo.exists(eventId)
        .orDie()
        .onFalseFail { EventError.NotFound }

    fun checkEventPublished(
        id: UUID,
    ): App<EventError, Unit> = EventRepo.getPublished(id)
        .orDie()
        .failIf({ it != true }) { EventError.NotFound }
        .discard()

    fun getOpenForRegistrationType(
        eventId: UUID,
    ): App<EventError, OpenForRegistrationType> = KIO.comprehension {

        !checkEventExisting(eventId)

        val isRegular = !EventRepo.isOpenForRegistration(eventId, LocalDateTime.now()).orDie()
        val type = if (isRegular) {
            OpenForRegistrationType.REGULAR
        } else {
            val isLate = !EventRepo.isOpenForLateRegistration(eventId, LocalDateTime.now()).orDie()
            if (isLate) {
                OpenForRegistrationType.LATE
            } else {
                OpenForRegistrationType.CLOSED
            }
        }

        KIO.ok(type)
    }

    fun getEventsForExport(): App<Nothing, ApiResponse.ListDto<EventForExportDto>> =
        EventRepo.getEventsForExport().orDie().andThen { list ->
            list.traverse {
                it.toDto()
            }
        }.map { ApiResponse.ListDto(it) }

    fun checkIsChallengeEvent(
        eventId: UUID,
    ): App<EventError, Boolean> = EventRepo.isChallengeEvent(eventId)
        .orDie()
        .onNullFail { EventError.NotFound }
}