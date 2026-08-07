package de.lambda9.ready2race.backend.app.certificate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

enum class AwardCertificateError : ServiceError {
    MissingTemplate,
    NoResults,
    CompetitionNotInEvent,

    /**
     * Siegerurkunden auf einem Challenge-Event. Bis zuletzt lief dieser Fall in NoResults ("No
     * placed teams for these certificates") - das Ergebnis stimmte zufällig, die Begründung nicht:
     * ein Challenge-Event hat keine Läufe und keine Platzierungen, es gibt hier grundsätzlich keine
     * Siegerurkunden. Wer "keine platzierten Teams" liest, wartet auf Ergebnisse, die nie kommen.
     */
    IsChallengeEvent,
    UnreadableTemplate;

    override fun respond(): ApiError = when (this) {
        MissingTemplate -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "There is no template assigned for award certificates",
            errorCode = ErrorCode.AWARD_CERTIFICATE_MISSING_TEMPLATE,
        )

        NoResults -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No placed teams for these certificates",
            errorCode = ErrorCode.AWARD_CERTIFICATE_NO_RESULTS,
        )

        CompetitionNotInEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Competition does not belong to this event",
            errorCode = ErrorCode.AWARD_CERTIFICATE_COMPETITION_NOT_IN_EVENT,
        )

        IsChallengeEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Award certificates are not available for a challenge event",
            errorCode = ErrorCode.AWARD_CERTIFICATE_IS_CHALLENGE_EVENT,
        )

        UnreadableTemplate -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The assigned award certificate template cannot be read",
            errorCode = ErrorCode.AWARD_CERTIFICATE_UNREADABLE_TEMPLATE,
        )
    }
}
