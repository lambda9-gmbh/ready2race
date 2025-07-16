package de.lambda9.ready2race.backend.app.teamTracking.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class TeamTrackingError : ServiceError {
    TeamNotFound,
    TeamAlreadyCheckedIn,
    TeamNotCheckedIn,
    QrCodeNotFound,
    QrCodeNotAssociatedWithParticipant;

    override fun respond(): ApiError = when (this) {
        TeamNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Team not found")
        TeamAlreadyCheckedIn -> ApiError(status = HttpStatusCode.Conflict, message = "Team is already checked in")
        TeamNotCheckedIn -> ApiError(status = HttpStatusCode.BadRequest, message = "Team is not checked in")
        QrCodeNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "QR code not found")
        QrCodeNotAssociatedWithParticipant -> ApiError(status = HttpStatusCode.BadRequest, message = "QR code not associated with a participant")
    }
}