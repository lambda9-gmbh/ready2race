package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class ParticipantError : ServiceError {
    ParticipantInUse,
    ParticipantNotFound;

    override fun respond(): ApiError = when (this) {
        ParticipantInUse -> ApiError(status = HttpStatusCode.Forbidden, message = "Participant can not be deleted")
        ParticipantNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Participant not found")
    }
}