package de.lambda9.ready2race.backend.app.certificate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class CertificateError : ServiceError {
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
    }
}