package de.lambda9.ready2race.backend.app.eventParticipant.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class EventParticipantError : ServiceError{
    NoChallengeWithSelfSubmission,
    NoEmail;

    override fun respond(): ApiError = when(this) {
        NoChallengeWithSelfSubmission ->
            ApiError(
                status = HttpStatusCode.Conflict,
                message = "Can only send access token for challenge events with self submission enabled",
                //TODO: error-code
            )

        NoEmail ->
            ApiError(
                status = HttpStatusCode.Conflict,
                message = "Can only send access token to participants with email address",
                //TODO: error-code
            )
    }
}