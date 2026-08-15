package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface ParticipantRequirementError : ServiceError {

    data class InvalidConfig(val details: Pair<String, String>) : ParticipantRequirementError;

    data object NotFound : ParticipantRequirementError;

    /**
     * Eine Bedingung mit `perCompetition` lässt sich nicht ohne Wettkampf abgleichen: Der
     * Abgleich ersetzt den vollständigen Zustand, und ohne Rahmen wäre unklar, welchen. Der
     * Aufruf ohne Wettkampf löschte sonst die Bestätigungen aller Wettkämpfe.
     */
    data object CompetitionRequired : ParticipantRequirementError;
    data object InUse : ParticipantRequirementError;

    override fun respond(): ApiError = when (this) {
        is InvalidConfig -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Invalid Config",
            details = mapOf(details)
        )

        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "ParticipantRequirement not found")
        CompetitionRequired -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "This requirement is checked per competition - the approval needs a competition"
        )
        InUse -> ApiError(status = HttpStatusCode.Conflict, message = "ParticipantRequirement is in use")
    }
}