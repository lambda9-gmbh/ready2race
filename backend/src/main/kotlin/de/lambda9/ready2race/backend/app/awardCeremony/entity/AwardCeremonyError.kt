package de.lambda9.ready2race.backend.app.awardCeremony.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

enum class AwardCeremonyError : ServiceError {
    NoResults,
    CompetitionNotInEvent,
    UnknownRatingCategory,

    /**
     * Ein Challenge-Event hat weder Läufe noch Platzierungen; eine Siegerehrung gibt es dort
     * grundsätzlich nicht. Eigener Fehler statt NoResults, damit die Antwort nicht nach „noch
     * nicht fertig" klingt und das Büro auf Ergebnisse wartet, die nie kommen.
     */
    IsChallengeEvent;

    override fun respond(): ApiError = when (this) {
        NoResults -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No placed teams for these ceremonies",
            errorCode = ErrorCode.AWARD_CEREMONY_NO_RESULTS,
        )

        CompetitionNotInEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Competition does not belong to this event",
            errorCode = ErrorCode.AWARD_CEREMONY_COMPETITION_NOT_IN_EVENT,
        )

        UnknownRatingCategory -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "There is no such rating category with placed teams in this competition",
            errorCode = ErrorCode.AWARD_CEREMONY_UNKNOWN_RATING_CATEGORY,
        )

        IsChallengeEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Award ceremony sheets are not available for a challenge event",
            errorCode = ErrorCode.AWARD_CEREMONY_IS_CHALLENGE_EVENT,
        )
    }
}
