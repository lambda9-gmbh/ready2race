package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasCompetitionRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventDay.control.eventDayDto
import de.lambda9.ready2race.backend.app.eventDay.control.toRecord
import de.lambda9.ready2race.backend.app.eventDay.entity.*
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasCompetitionRecord
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object EventDayService {

    fun addEventDay(
        request: EventDayRequest,
        userId: UUID,
        eventId: UUID
    ): App<EventError, ApiResponse.Created> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val record = !request.toRecord(userId, eventId)
        val id = !EventDayRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(id))
    }

    fun pageByEvent(
        eventId: UUID,
        params: PaginationParameters<EventDaySort>,
        competitionId: UUID?,
        scope: Privilege.Scope?
    ): App<ServiceError, ApiResponse.Page<EventDayDto, EventDaySort>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val total =
            if (competitionId == null) !EventDayRepo.countByEvent(eventId, params.search, scope).orDie()
            else !EventDayRepo.countByEventAndCompetition(eventId, competitionId, params.search).orDie()

        val page =
            if (competitionId == null) !EventDayRepo.pageByEvent(eventId, params, scope).orDie()
            else !EventDayRepo.pageByEventAndCompetition(eventId, competitionId, params).orDie()

        page.traverse { it.eventDayDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEventDay(
        eventDayId: UUID,
        scope: Privilege.Scope?
    ): App<EventDayError, ApiResponse> = KIO.comprehension {
        val eventDay = !EventDayRepo.getEventDay(eventDayId, scope).orDie().onNullFail { EventDayError.EventDayNotFound }
        eventDay.eventDayDto().map { ApiResponse.Dto(it) }
    }

    fun updateEvent(
        request: EventDayRequest,
        userId: UUID,
        eventDayId: UUID,
    ): App<EventDayError, ApiResponse.NoData> =
        EventDayRepo.update(eventDayId) {
            date = request.date
            name = request.name
            description = request.description
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { EventDayError.EventDayNotFound }
            .map { ApiResponse.NoData }

    fun deleteEvent(
        id: UUID
    ): App<EventDayError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !EventDayRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(EventDayError.EventDayNotFound)
        } else {
            noData
        }
    }

    fun updateEventDayHasCompetition(
        request: AssignCompetitionsToDayRequest,
        userId: UUID,
        eventDayId: UUID
    ): App<EventDayError, ApiResponse.NoData> = KIO.comprehension {

        val eventDayExists = !EventDayRepo.exists(eventDayId).orDie()
        if (!eventDayExists) KIO.fail(EventDayError.EventDayNotFound)

        val unknownCompetitions = !CompetitionRepo.findUnknown(request.competitions).orDie()
        if (unknownCompetitions.isNotEmpty()) KIO.fail(EventDayError.CompetitionsNotFound(unknownCompetitions))

        !EventDayHasCompetitionRepo.deleteByEventDay(eventDayId).orDie()
        !EventDayHasCompetitionRepo.create(request.competitions.map {
            EventDayHasCompetitionRecord(
                eventDay = eventDayId,
                competition = it,
                createdAt = LocalDateTime.now(),
                createdBy = userId
            )
        }).orDie()

        noData
    }
}