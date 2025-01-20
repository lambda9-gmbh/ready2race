package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventDay.control.eventDayDto
import de.lambda9.ready2race.backend.app.eventDay.control.record
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayDto
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDayRequest
import de.lambda9.ready2race.backend.app.eventDay.entity.EventDaySort
import de.lambda9.ready2race.backend.http.ApiError
import de.lambda9.ready2race.backend.http.ApiResponse
import de.lambda9.ready2race.backend.http.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.http.PaginationParameters
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import java.time.LocalDateTime
import java.util.*

object EventDayService {

    enum class EventDayError : ServiceError {
        EventDayNotFound;

        override fun respond(): ApiError = when (this) {
            EventDayNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "EventDay Not Found")
        }
    }

    fun addEventDay(
        request: EventDayRequest,
        userId: UUID,
        eventId: UUID
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val id = !EventDayRepo.create(request.record(userId, eventId)).orDie()
        KIO.ok(ApiResponse.Created(id))
    }

    fun page(
        eventId: UUID,
        params: PaginationParameters<EventDaySort>
    ): App<Nothing, ApiResponse.Page<EventDayDto, EventDaySort>> = KIO.comprehension {
        val total = !EventDayRepo.count(eventId, params.search).orDie()
        val page = !EventDayRepo.page(eventId, params).orDie()

        page.forEachM { it.eventDayDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEventDayById(
        eventDayId: UUID
    ): App<EventDayError, ApiResponse> = KIO.comprehension {
        val eventDay = !EventDayRepo.getEventDay(eventDayId).orDie().onNullFail { EventDayError.EventDayNotFound }
        eventDay.eventDayDto().map { ApiResponse.Dto(it) }
    }

    fun updateEvent(
        request: EventDayRequest,
        eventDayId: UUID,
        eventId: UUID,
        userId: UUID,
    ): App<EventDayError, ApiResponse.NoData> = KIO.comprehension {
        !EventDayRepo.update(eventDayId) {
            event = eventId
            date = request.properties.date
            name = request.properties.name
            description = request.properties.description
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        noData
    }

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
}


