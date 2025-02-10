package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*
import java.util.*


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