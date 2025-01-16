package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.control.eventDto
import de.lambda9.ready2race.backend.app.event.control.record
import de.lambda9.ready2race.backend.app.event.entity.EventDto
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.http.ApiError
import de.lambda9.ready2race.backend.http.ApiResponse
import de.lambda9.ready2race.backend.http.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.http.PaginationParameters
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import java.time.LocalDateTime.now
import java.util.*

object EventService {

    private val logger = KotlinLogging.logger {}

    enum class EventError : ServiceError {
        EventNotFound;

        override fun respond(): ApiError = when (this) {
            EventNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Event not found")
        }
    }

    fun addEvent(
        request: EventRequest,
        userId: UUID
    ): App<Nothing, ApiResponse.ID> = KIO.comprehension {
        val id = !EventRepo.create(
            EventDto(
                id = UUID.randomUUID(),
                properties = request.properties
            ).record(userId)
        ).orDie()
        KIO.ok(
            ApiResponse.ID(id)
        )
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

    fun getEventById(
        id: UUID
    ): App<EventError, ApiResponse> = KIO.comprehension {
        val event = !EventRepo.getEvent(id).orDie().onNullFail { EventError.EventNotFound }
        event.eventDto().map { ApiResponse.Dto(it) }
    }

    fun updateEvent(
        request: EventRequest,
        eventId: UUID,
        userId: UUID,
    ): App<EventError, ApiResponse.NoData> = KIO.comprehension {
        !EventRepo.update(eventId){
            name = request.properties.name
            description = request.properties.description
            location = request.properties.location
            registrationAvailableFrom = request.properties.registrationAvailableFrom
            registrationAvailableTo = request.properties.registrationAvailableTo
            paymentDueDate = request.properties.paymentDueDate
            invoicePrefix = request.properties.invoicePrefix
            updatedBy = userId
            updatedAt = now() // todo: this "now()"?
        }.orDie() // todo: Not very useful to use a empty function here

        noData
    }
}