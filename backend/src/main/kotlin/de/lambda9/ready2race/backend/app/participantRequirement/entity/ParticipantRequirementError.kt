package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface ParticipantRequirementError : ServiceError {

    data class InvalidConfig(val details: Pair<String, String>) : ParticipantRequirementError;

    data object NotFound : ParticipantRequirementError;
    data object InUse : ParticipantRequirementError;

    override fun respond(): ApiError = when (this) {
        is InvalidConfig -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Invalid Config",
            details = mapOf(details)
        )

        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "ParticipantRequirement not found")
        InUse -> ApiError(status = HttpStatusCode.Conflict, message = "ParticipantRequirement is in use")
    }
}