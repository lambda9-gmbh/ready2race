package de.lambda9.ready2race.backend.app.participantTracking.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

/**
 * Check-in und Check-out am Steg. Die vier Gründe teilten sich zwei feste Texte ("Check-in
 * fehlgeschlagen" / "Check-out fehlgeschlagen") - dabei ist "ist schon eingecheckt" gar keine
 * Störung, sondern der Hinweis, dass nichts mehr zu tun ist.
 */
enum class ParticipantTrackingError : ServiceError {
    TeamAlreadyCheckedIn,
    TeamNotCheckedIn,
    QrCodeNotFound,
    QrCodeNotAssociatedWithParticipant;

    override fun respond(): ApiError = when (this) {
        TeamAlreadyCheckedIn -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Team is already checked in",
            errorCode = ErrorCode.TRACKING_TEAM_ALREADY_CHECKED_IN,
        )

        TeamNotCheckedIn -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Team is not checked in",
            errorCode = ErrorCode.TRACKING_TEAM_NOT_CHECKED_IN,
        )

        QrCodeNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "QR code not found",
            errorCode = ErrorCode.TRACKING_QR_CODE_NOT_FOUND,
        )

        QrCodeNotAssociatedWithParticipant -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "QR code not associated with a participant",
            errorCode = ErrorCode.TRACKING_QR_CODE_NOT_ASSOCIATED_WITH_PARTICIPANT,
        )
    }
}
