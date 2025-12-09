package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface CompetitionExecutionChallengeError : ServiceError {

    data object NotAChallengeEvent : CompetitionExecutionChallengeError
    data object ChallengeAlreadyStarted : CompetitionExecutionChallengeError
    data object ChallengeNotStartedYet : CompetitionExecutionChallengeError
    data object CorruptedSetup : CompetitionExecutionChallengeError
    data object ResultAlreadySubmitted : CompetitionExecutionChallengeError
    data object NoResultSubmitted : CompetitionExecutionChallengeError
    data object SelfSubmissionNotAllowed : CompetitionExecutionChallengeError

    override fun respond(): ApiError = when (this) {
        NotAChallengeEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Event is not a challenge event"
        )

        ChallengeAlreadyStarted -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The challenge has already started"
        )

        ChallengeNotStartedYet -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The challenge has not started yet"
        )

        CorruptedSetup -> ApiError(
            status = HttpStatusCode.InternalServerError,
            message = "The competition setup is corrupted and does not behave as expected. Contact an administrator."
        )

        ResultAlreadySubmitted -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The results for this team have already been submitted"
        )

        NoResultSubmitted -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The results for this team have not been submitted yet"
        )

        SelfSubmissionNotAllowed -> ApiError(
            status = HttpStatusCode.Forbidden,
            message = "Self submission of results is not allowed for this event"
        )
    }
}