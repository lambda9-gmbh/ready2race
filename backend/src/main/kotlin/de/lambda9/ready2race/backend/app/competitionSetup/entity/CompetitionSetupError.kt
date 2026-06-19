package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface CompetitionSetupError : ServiceError {
    data object NotFound : CompetitionSetupError
    data object CompetitionPropertiesNotFound : CompetitionSetupError
    data object RoundNotFound : CompetitionSetupError
    data object IsChallengeEvent : CompetitionSetupError
    data object CreatedRoundDeleted : CompetitionSetupError
    data object CreatedRoundOrderChanged : CompetitionSetupError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "CompetitionSetup not found")
        CompetitionPropertiesNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "CompetitionProperties not found"
        )

        RoundNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "CompetitionSetupRound not found")
        IsChallengeEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Setup can not be modified on a challenge event"
        )

        CreatedRoundDeleted -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "A round that has already been created during execution can not be deleted"
        )

        CreatedRoundOrderChanged -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Rounds that have already been created during execution can not be reordered, " +
                "and no round can be inserted before them"
        )
    }
}