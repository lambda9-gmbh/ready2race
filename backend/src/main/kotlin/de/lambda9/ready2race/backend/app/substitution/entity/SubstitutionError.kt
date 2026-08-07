package de.lambda9.ready2race.backend.app.substitution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

/**
 * Warum eine Ummeldung abgelehnt wird. Sieben Gründe, die sich im Schiedsrichter-Dashboard bislang
 * ein bis zwei Texte teilten - und die beiden vorhandenen Texte griffen wegen vertauschter
 * i18n-Schlüssel in de/da nicht einmal, sodass der rohe Schlüssel in der Oberfläche stand.
 */
enum class SubstitutionError : ServiceError {
    NotFound,
    ParticipantOutNotFound,
    ParticipantInNotFound,
    ParticipantOutNotAvailableForSubstitution,
    ParticipantInNotAvailableForSubstitution,
    DependentSubstitutionFound,
    CreatedInPreviousRound;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Substitution not found",
            errorCode = ErrorCode.SUBSTITUTION_NOT_FOUND,
        )

        ParticipantOutNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "ParticipantOut not found",
            errorCode = ErrorCode.SUBSTITUTION_PARTICIPANT_OUT_NOT_FOUND,
        )

        ParticipantInNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "ParticipantIn not found",
            errorCode = ErrorCode.SUBSTITUTION_PARTICIPANT_IN_NOT_FOUND,
        )

        ParticipantOutNotAvailableForSubstitution -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "ParticipantOut is not available for substitution",
            errorCode = ErrorCode.SUBSTITUTION_PARTICIPANT_OUT_NOT_AVAILABLE,
        )

        ParticipantInNotAvailableForSubstitution -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "ParticipantIn is not available for substitution",
            errorCode = ErrorCode.SUBSTITUTION_PARTICIPANT_IN_NOT_AVAILABLE,
        )

        DependentSubstitutionFound -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Later substitution found that depends on this substitution",
            errorCode = ErrorCode.SUBSTITUTION_DEPENDENT_FOUND,
        )

        CreatedInPreviousRound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "This substitution was created in a previous round",
            errorCode = ErrorCode.SUBSTITUTION_CREATED_IN_PREVIOUS_ROUND,
        )
    }
}
