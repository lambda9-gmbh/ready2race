package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class CompetitionExecutionError : ServiceError {
    NoSetupRoundFound,
    NoRegistrations,
    NotEnoughSetupTeams,
    NotAllPlacesSet;

    override fun respond(): ApiError = when (this) {
        NoSetupRoundFound -> ApiError(status = HttpStatusCode.BadRequest, message = "No setup round found")
        NoRegistrations -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No registrations for this competition"
        )
        NotEnoughSetupTeams -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "More registrations than the setup has allowed"
        )
        NotAllPlacesSet -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Not all places are set in the current round"
        )
    }
}