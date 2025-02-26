package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*
import java.util.*


sealed interface EventDayError : ServiceError {
    data object EventDayNotFound : EventDayError

    data class CompetitionsNotFound(val competitions: List<UUID>) : EventDayError

    override fun respond(): ApiError = when (this) {
        EventDayNotFound -> ApiError(HttpStatusCode.NotFound, message = "EventDay not found")
        is CompetitionsNotFound -> ApiError(
            HttpStatusCode.BadRequest,
            message = "Competitions not found",
            details = mapOf("unknownIds" to competitions)
        )
    }
}