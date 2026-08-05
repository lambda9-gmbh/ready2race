package de.lambda9.ready2race.backend.app.competitionDeregistration.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

enum class CompetitionDeregistrationError : ServiceError {
    NotFound,
    AlreadyExists,
    IsLocked,
    ResultsAlreadyExists,
    NotInCurrentRound,
    RegistrationStillOpen;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "CompetitionDeregistration not found")
        AlreadyExists -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "CompetitionDeregistration already exists",
            errorCode = ErrorCode.DEREGISTRATION_ALREADY_EXISTS
        )

        IsLocked -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "CompetitionDeregistration was created before this round and is therefore locked",
            errorCode = ErrorCode.DEREGISTRATION_IS_LOCKED
        )

        // Not "all results are in" - a single boat with a place or an elimination is enough, because
        // that already fixes the field of the match (see CompetitionDeregistrationLogic).
        ResultsAlreadyExists -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The current match of this team is already being scored. Deregistration will have to be handled in the results directly.",
            errorCode = ErrorCode.DEREGISTRATION_RESULTS_ALREADY_EXIST
        )

        NotInCurrentRound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The team is not present in the current round. It has either already dropped out or never participated to begin with.",
            errorCode = ErrorCode.DEREGISTRATION_NOT_IN_CURRENT_ROUND
        )

        RegistrationStillOpen -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The registration is still open.",
            errorCode = ErrorCode.DEREGISTRATION_REGISTRATION_STILL_OPEN
        )
    }
}
