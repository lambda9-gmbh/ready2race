package de.lambda9.ready2race.backend.app.certificate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

/**
 * Warum eine Teilnahmeurkunde nicht ausgegeben wird. Die Meldungen standen bis zuletzt als roher
 * englischer Text in der Oberfläche (ParticipantForEventTable gab `error.message` direkt aus) -
 * jeder Grund trägt deshalb jetzt einen ErrorCode, damit das Frontend übersetzen kann.
 */
enum class CertificateError : ServiceError {
    NoResults,
    MissingTemplate,
    NotAChallengeEvent,
    ChallengeStillInProgress,
    UnreadableTemplate;

    override fun respond(): ApiError = when (this) {
        NotAChallengeEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Event is not a challenge event",
            errorCode = ErrorCode.CERTIFICATE_NOT_A_CHALLENGE_EVENT,
        )

        ChallengeStillInProgress -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Challenge Event is still in progress",
            errorCode = ErrorCode.CERTIFICATE_CHALLENGE_STILL_IN_PROGRESS,
        )

        NoResults -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No results in this event for this participant",
            errorCode = ErrorCode.CERTIFICATE_NO_RESULTS,
        )

        MissingTemplate -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "There is no template assigned for this type of certificate",
            errorCode = ErrorCode.CERTIFICATE_MISSING_TEMPLATE,
        )

        UnreadableTemplate -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The assigned certificate of participation template cannot be read",
            errorCode = ErrorCode.CERTIFICATE_UNREADABLE_TEMPLATE,
        )
    }
}
