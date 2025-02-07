package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasRaceRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventDay.control.eventDayDto
import de.lambda9.ready2race.backend.app.eventDay.control.toRecord
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayDto
import de.lambda9.ready2race.backend.app.eventDay.entity.AssignRacesToDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.app.race.control.RaceRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasRaceRecord
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import java.time.LocalDateTime
import java.util.*

object EventDayService {


    sealed interface EventDayError : ServiceError {
        data object EventDayNotFound : EventDayError

        data class RacesNotFound(val races: List<UUID>) : EventDayError

        override fun respond(): ApiError = when (this) {
            EventDayNotFound -> ApiError(HttpStatusCode.NotFound, message = "EventDay not found")
            is RacesNotFound -> ApiError(
                HttpStatusCode.BadRequest,
                message = "Races not found",
                details = mapOf("unknownIds" to races)
            )
        }
    }

    fun addEventDay(
        request: EventDayRequest,
        userId: UUID,
        eventId: UUID
    ): App<EventService.EventError, ApiResponse.Created> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val record = !request.toRecord(userId, eventId)
        val id = !EventDayRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(id))
    }

    fun pageByEvent(
        eventId: UUID,
        params: PaginationParameters<EventDaySort>,
        raceId: UUID?
    ): App<ServiceError, ApiResponse.Page<EventDayDto, EventDaySort>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val total =
            if (raceId == null) !EventDayRepo.countByEvent(eventId, params.search).orDie()
            else !EventDayRepo.countByEventAndRace(eventId, raceId, params.search).orDie()

        val page =
            if (raceId == null) !EventDayRepo.pageByEvent(eventId, params).orDie()
            else !EventDayRepo.pageByEventAndRace(eventId, raceId, params).orDie()

        page.forEachM { it.eventDayDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEventDay(
        eventDayId: UUID
    ): App<EventDayError, ApiResponse> = KIO.comprehension {
        val eventDay = !EventDayRepo.getEventDay(eventDayId).orDie().onNullFail { EventDayError.EventDayNotFound }
        eventDay.eventDayDto().map { ApiResponse.Dto(it) }
    }

    fun updateEvent(
        request: EventDayRequest,
        userId: UUID,
        eventDayId: UUID,
    ): App<EventDayError, ApiResponse.NoData> =
        EventDayRepo.update(eventDayId) {
            date = request.properties.date
            name = request.properties.name
            description = request.properties.description
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

    fun updateEventDayHasRace(
        request: AssignRacesToDayRequest,
        userId: UUID,
        eventDayId: UUID
    ): App<EventDayError, ApiResponse.NoData> = KIO.comprehension {

        val eventDayExists = !EventDayRepo.exists(eventDayId).orDie()
        if (!eventDayExists) KIO.fail(EventDayError.EventDayNotFound)

        val unknownRaces = !RaceRepo.findUnknown(request.races).orDie()
        if (unknownRaces.isNotEmpty()) KIO.fail(EventDayError.RacesNotFound(unknownRaces))

        !EventDayHasRaceRepo.deleteByEventDay(eventDayId).orDie()
        !EventDayHasRaceRepo.create(request.races.map {
            EventDayHasRaceRecord(
                eventDay = eventDayId,
                race = it,
                createdAt = LocalDateTime.now(),
                createdBy = userId
            )
        }).orDie()

        noData
    }
}