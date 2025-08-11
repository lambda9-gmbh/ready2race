package de.lambda9.ready2race.backend.app.participantTracking.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class ParticipantTrackingError : ServiceError {
    TeamAlreadyCheckedIn,
    TeamNotCheckedIn,
    QrCodeNotFound,
    QrCodeNotAssociatedWithParticipant;

    override fun respond(): ApiError = when (this) {
        TeamAlreadyCheckedIn -> ApiError(status = HttpStatusCode.Conflict, message = "Team is already checked in")
        TeamNotCheckedIn -> ApiError(status = HttpStatusCode.BadRequest, message = "Team is not checked in")
        QrCodeNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "QR code not found")
        QrCodeNotAssociatedWithParticipant -> ApiError(status = HttpStatusCode.BadRequest, message = "QR code not associated with a participant")
    }
}