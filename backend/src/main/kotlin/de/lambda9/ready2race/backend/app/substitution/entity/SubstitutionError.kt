package de.lambda9.ready2race.backend.app.substitution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class SubstitutionError : ServiceError {
    NotFound,
    ParticipantOutNotFound,
    ParticipantInNotFound,
    ParticipantOutNotAvailableForSubstitution,
    ParticipantInNotAvailableForSubstitution;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Substitution not found")
        ParticipantOutNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "ParticipantOut not found")
        ParticipantInNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "ParticipantIn not found")
        ParticipantOutNotAvailableForSubstitution -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "ParticipantOut is not available for substitution"
        )
        ParticipantInNotAvailableForSubstitution -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "ParticipantIn is not available for substitution"
        )
    }
}