package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.UUID

sealed interface EventInfoProblem : ServiceError {
    data class InfoViewConfigurationNotFound(val id: UUID) : EventInfoProblem
    data class EventNotFound(val eventId: UUID) : EventInfoProblem
    data class InvalidFilter(val filterMessage: String) : EventInfoProblem
    data class InvalidViewConfiguration(val configMessage: String) : EventInfoProblem

    override fun respond(): ApiError = when (this) {
        is InfoViewConfigurationNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Info view configuration with id $id not found"
        )

        is EventNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Event with id $eventId not found"
        )

        is InvalidFilter -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Invalid filter: $filterMessage"
        )

        is InvalidViewConfiguration -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Invalid view configuration: $configMessage"
        )
    }
}