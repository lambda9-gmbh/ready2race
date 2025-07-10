package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class CompetitionExecutionError : ServiceError {
    MatchNotFound,
    MatchTeamNotFound,
    NoRoundsInSetup,
    AllRoundsCreated,
    NoSetupMatchesInRound,
    NoRegistrations,
    RegistrationsNotFinalized,
    NotEnoughTeamSpace,
    NotAllPlacesSet,
    TeamsNotMatching,
    RoundNotFound,
    MatchResultsLocked,
    StartTimeNotSet;

    override fun respond(): ApiError = when (this) {
        MatchNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Competition match not found",
        )

        MatchTeamNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Team not found",
        )

        NoRoundsInSetup -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Competition setup has no rounds defined",
        )

        AllRoundsCreated -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "All rounds have already been created",
        )

        NoSetupMatchesInRound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No setup matches found for next round",
        )

        NoRegistrations -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No registrations for this competition",
        )

        RegistrationsNotFinalized -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Registrations for this competition have not been finalized",
        )

        NotEnoughTeamSpace -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "More registrations than the setup has allowed",
        )

        NotAllPlacesSet -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Not all places are set in the current round",
        )

        TeamsNotMatching -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The specified teams do not match the actual teams of the match"
        )

        RoundNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Round not found",
        )

        MatchResultsLocked -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Match results locked. Only results of the latest round can be edited.",
        )

        StartTimeNotSet -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "StartTime not set",
        )
    }
}