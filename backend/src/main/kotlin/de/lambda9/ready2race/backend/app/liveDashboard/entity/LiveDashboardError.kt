package de.lambda9.ready2race.backend.app.liveDashboard.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.UUID

sealed interface LiveDashboardError : ServiceError {
    data class EventNotFound(val eventId: UUID) : LiveDashboardError
    data class TeamNotFound(val teamId: UUID) : LiveDashboardError

    /** Event steht auf chainProgressionMode = REGATTABUERO: Beenden geht dort nur über den Zeitplan. */
    data object FinishReservedForOffice : LiveDashboardError

    override fun respond(): ApiError = when (this) {
        is EventNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Event with id $eventId not found"
        )

        is TeamNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Team with id $teamId not found in this match"
        )

        FinishReservedForOffice -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Finishing is handled by the regatta office for this event"
        )
    }
}
