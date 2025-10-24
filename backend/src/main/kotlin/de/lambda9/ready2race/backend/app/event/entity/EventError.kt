package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class EventError : ServiceError {
    NotFound,
    ChallengeResultTypeNotAllowed,
    NoChallengeResultTypeProvided;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Event not found")
        ChallengeResultTypeNotAllowed -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The challenge result type is not allowed in non-challenge events"
        )

        NoChallengeResultTypeProvided -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Challenge result type was not provided but is necessary for challenge events"
        )
    }
}