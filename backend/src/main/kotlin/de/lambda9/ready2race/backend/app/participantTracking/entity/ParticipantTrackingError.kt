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
    QrCodeNotAssociatedWithParticipant,
    TrackingEntryNotFound,
    SequenceConflict,
    TimestampCollision;

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

        TrackingEntryNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Tracking entry not found",
            errorCode = ErrorCode.TRACKING_ENTRY_NOT_FOUND,
        )

        SequenceConflict -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Check-in and check-out times would contradict each other",
            errorCode = ErrorCode.TRACKING_SEQUENCE_CONFLICT,
        )

        TimestampCollision -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Another entry for this participant already exists at that exact time",
            errorCode = ErrorCode.TRACKING_TIMESTAMP_COLLISION,
        )
    }
}
