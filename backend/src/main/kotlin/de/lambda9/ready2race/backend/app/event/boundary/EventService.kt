package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.control.eventDto
import de.lambda9.ready2race.backend.app.event.control.toRecord
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object EventService {

    fun addEvent(
        request: EventRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        EventRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun page(
        params: PaginationParameters<EventSort>,
        scope: Privilege.Scope,
    ): App<Nothing, ApiResponse.Page<EventDto, EventSort>> = KIO.comprehension {
        val total = !EventRepo.count(params.search, scope).orDie()
        val page = !EventRepo.page(params, scope).orDie()

        page.traverse { it.eventDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEvent(
        id: UUID,
        scope: Privilege.Scope
    ): App<EventError, ApiResponse.Dto<EventDto>> = KIO.comprehension {
        val event = !EventRepo.getEvent(id, scope).orDie().onNullFail { EventError.NotFound }
        event.eventDto().map { ApiResponse.Dto(it) }
    }

    fun updateEvent(
        request: EventRequest,
        userId: UUID,
        eventId: UUID,
    ): App<EventError, ApiResponse.NoData> =
        EventRepo.update(eventId) {
            name = request.name
            description = request.description
            location = request.location
            registrationAvailableFrom = request.registrationAvailableFrom
            registrationAvailableTo = request.registrationAvailableTo
            invoicePrefix = request.invoicePrefix
            published = request.published
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { EventError.NotFound }
            .map { ApiResponse.NoData }

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
}