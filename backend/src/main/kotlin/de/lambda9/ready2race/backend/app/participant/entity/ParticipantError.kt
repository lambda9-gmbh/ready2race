package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface ParticipantError : ServiceError {
    data object ParticipantInUse : ParticipantError
    data object ParticipantNotFound : ParticipantError

    override fun respond(): ApiError = when (this) {
        ParticipantInUse -> ApiError(status = HttpStatusCode.Forbidden, message = "Participant can not be deleted")
        ParticipantNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Participant not found")
    }
}