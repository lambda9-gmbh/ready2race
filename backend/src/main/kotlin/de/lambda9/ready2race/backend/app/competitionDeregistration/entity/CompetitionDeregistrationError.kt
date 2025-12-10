package de.lambda9.ready2race.backend.app.competitionDeregistration.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
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
            message = "CompetitionDeregistration already exists"
        )

        IsLocked -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "CompetitionDeregistration was created before this round and is therefore locked"
        )

        ResultsAlreadyExists -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "In the current round the results were already entered. Deregistration will have to be handled in the results directly."
        )

        NotInCurrentRound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The team is not present in the current round. It has either already dropped out or never participated to begin with."
        )

        RegistrationStillOpen -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The registration is still open."
        )
    }
}