package de.lambda9.ready2race.backend.app.liveDashboard.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
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

        // Der häufigste Fehlerfall am Steg: das Steg-Personal drückt "Beenden", die Veranstaltung
        // steht aber auf REGATTABUERO. Bisher las sich das als "Der Lauf konnte nicht geändert
        // werden" - also wie eine Störung, obwohl alles in Ordnung ist und schlicht jemand anderes
        // zuständig ist.
        FinishReservedForOffice -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Finishing is handled by the regatta office for this event",
            errorCode = ErrorCode.LIVE_DASHBOARD_FINISH_RESERVED_FOR_OFFICE,
        )
    }
}
