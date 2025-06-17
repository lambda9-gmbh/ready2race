package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

enum class CompetitionExecutionError : ServiceError {
    MatchNotFound,
    MatchTeamNotFound,
    NoRoundsInSetup,
    FinalRoundAlreadyCreated,
    NoSetupMatchesInRound,
    NoRegistrations,
    NotEnoughTeamSpace,
    NotAllPlacesSet;

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
            errorCode = ErrorCode.CE_SETUP_HAS_NO_ROUNDS
        )

        FinalRoundAlreadyCreated -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Final round has already been created",
            errorCode = ErrorCode.CE_FINAL_ROUND_ALREADY_CREATED
        )

        NoSetupMatchesInRound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No setup matches found for next round",
            errorCode = ErrorCode.CE_NO_SETUP_MATCHES_FOR_NEXT_ROUND
        )

        NoRegistrations -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No registrations for this competition",
            errorCode = ErrorCode.CE_NO_REGISTRATIONS_FOR_COMPETITION
        )

        NotEnoughTeamSpace -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "More registrations than the setup has allowed",
            errorCode = ErrorCode.CE_NOT_ENOUGH_TEAM_SPACE_IN_FIRST_ROUND
        )

        NotAllPlacesSet -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Not all places are set in the current round",
            errorCode = ErrorCode.CE_NOT_ALL_PLACES_SET
        )
    }
}