package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.UUID

sealed interface EventInfoProblem : ServiceError {
    data class BoardNotFound(val id: UUID) : EventInfoProblem
    data class EventNotFound(val eventId: UUID) : EventInfoProblem
    data class InvalidFilter(val filterMessage: String) : EventInfoProblem
    data class QrCodeNotFound(val qrCode: String) : EventInfoProblem

    override fun respond(): ApiError = when (this) {
        is BoardNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Board with id $id not found"
        )

        is EventNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Event with id $eventId not found"
        )

        is InvalidFilter -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Invalid filter: $filterMessage"
        )

        is QrCodeNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            // Bewusst dieselbe Antwort für "gibt es nicht", "gehört zu einer anderen
            // Veranstaltung" und "gehört zu einer Helferrolle": eine unterscheidbare
            // Meldung würde verraten, welche Codes existieren.
            message = "No participant found for this code"
        )
    }
}