package de.lambda9.ready2race.backend.app.certificate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class CertificateError : ServiceError {
    NoResults,
    MissingTemplate,
    NotAChallengeEvent,
    ChallengeStillInProgress;

    override fun respond(): ApiError = when (this) {
        NotAChallengeEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Event is not a challenge event"
        )

        ChallengeStillInProgress -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Challenge Event is still in progress"
        )

        NoResults -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No results in this event for this participant"
        )

        MissingTemplate -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "There is no template assigned for this type of certificate"
        )
    }
}