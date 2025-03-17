package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class ParticipantError : ServiceError {
    ParticipantNotFound;

    override fun respond(): ApiError = when (this) {
        ParticipantNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Participant not found")
    }
}