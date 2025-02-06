package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.control.eventDto
import de.lambda9.ready2race.backend.app.event.control.record
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.failIf
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

object EventService {


    enum class EventError : ServiceError {
        EventNotFound;

        override fun respond(): ApiError = when (this) {
            EventNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Event not found")
        }
    }

    fun addEvent(
        request: EventRequest,
        userId: UUID
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val id = !EventRepo.create(request.record(userId)).orDie()
        KIO.ok(ApiResponse.Created(id))
    }

    fun page(
        params: PaginationParameters<EventSort>,
    ): App<Nothing, ApiResponse.Page<EventDto, EventSort>> = KIO.comprehension {
        val total = !EventRepo.count(params.search).orDie()
        val page = !EventRepo.page(params).orDie()

        page.forEachM { it.eventDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEvent(
        id: UUID
    ): App<EventError, ApiResponse.Dto<EventDto>> = KIO.comprehension {
        val event = !EventRepo.getEvent(id).orDie().onNullFail { EventError.EventNotFound }
        event.eventDto().map { ApiResponse.Dto(it) }
    }

    fun updateEvent(
        request: EventRequest,
        userId: UUID,
        eventId: UUID,
    ): App<EventError, ApiResponse.NoData> =
        EventRepo.update(eventId) {
            name = request.properties.name
            description = request.properties.description
            location = request.properties.location
            registrationAvailableFrom = request.properties.registrationAvailableFrom
            registrationAvailableTo = request.properties.registrationAvailableTo
            invoicePrefix = request.properties.invoicePrefix
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onFalseFail { EventError.EventNotFound }
            .map { ApiResponse.NoData }

    fun deleteEvent(
        id: UUID
    ): App<EventError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !EventRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(EventError.EventNotFound)
        } else {
            noData
        }
    }

    fun checkEventExisting(
        eventId: UUID
    ): App<EventError, Unit> = EventRepo.exists(eventId)
        .orDie()
        .failIf(condition = { !it }) {
            EventError.EventNotFound
        }.map {}
}