package de.lambda9.ready2race.backend.app.certificate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class AwardCertificateError : ServiceError {
    MissingTemplate,
    NoResults,
    CompetitionNotInEvent,
    UnreadableTemplate;

    override fun respond(): ApiError = when (this) {
        MissingTemplate -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "There is no template assigned for award certificates"
        )

        NoResults -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No placed teams for these certificates"
        )

        CompetitionNotInEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Competition does not belong to this event"
        )

        UnreadableTemplate -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The assigned award certificate template cannot be read"
        )
    }
}
