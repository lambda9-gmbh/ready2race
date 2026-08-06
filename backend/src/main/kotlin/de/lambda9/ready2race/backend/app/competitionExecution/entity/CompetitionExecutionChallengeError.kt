package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

/**
 * Warum eine Challenge-Aktion abgelehnt wird. Alle sieben Gründe teilten sich in der Oberfläche
 * eine Sammelmeldung, obwohl sie Verschiedenes verlangen: warten, aufhören, jemand anderen bitten
 * oder einen Administrator holen.
 */
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
            message = "Event is not a challenge event",
            errorCode = ErrorCode.CHALLENGE_NOT_A_CHALLENGE_EVENT,
        )

        ChallengeAlreadyStarted -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The challenge has already started",
            errorCode = ErrorCode.CHALLENGE_ALREADY_STARTED,
        )

        ChallengeNotStartedYet -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The challenge has not started yet",
            errorCode = ErrorCode.CHALLENGE_NOT_STARTED_YET,
        )

        CorruptedSetup -> ApiError(
            status = HttpStatusCode.InternalServerError,
            message = "The competition setup is corrupted and does not behave as expected. Contact an administrator.",
            errorCode = ErrorCode.CHALLENGE_CORRUPTED_SETUP,
        )

        ResultAlreadySubmitted -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The results for this team have already been submitted",
            errorCode = ErrorCode.CHALLENGE_RESULT_ALREADY_SUBMITTED,
        )

        NoResultSubmitted -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The results for this team have not been submitted yet",
            errorCode = ErrorCode.CHALLENGE_NO_RESULT_SUBMITTED,
        )

        SelfSubmissionNotAllowed -> ApiError(
            status = HttpStatusCode.Forbidden,
            message = "Self submission of results is not allowed for this event",
            errorCode = ErrorCode.CHALLENGE_SELF_SUBMISSION_NOT_ALLOWED,
        )
    }
}
