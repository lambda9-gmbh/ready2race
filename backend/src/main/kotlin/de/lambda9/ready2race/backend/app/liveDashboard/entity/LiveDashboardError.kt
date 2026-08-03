package de.lambda9.ready2race.backend.app.liveDashboard.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.UUID

sealed interface LiveDashboardError : ServiceError {
    data class EventNotFound(val eventId: UUID) : LiveDashboardError

    override fun respond(): ApiError = when (this) {
        is EventNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Event with id $eventId not found"
        )
    }
}
