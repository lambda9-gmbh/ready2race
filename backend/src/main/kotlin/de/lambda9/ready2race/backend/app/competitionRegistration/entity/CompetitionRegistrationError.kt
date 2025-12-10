package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class CompetitionRegistrationError : ServiceError {
    RegistrationClosed,
    EventRegistrationNotFound,
    RegistrationInvalid,
    DuplicateParticipant,
    NotFound,
    RatingCategoryMissing,
    ParticipantOutOfAgeRestriction,
    RoundAlreadyExisting;

    override fun respond(): ApiError = when (this) {

        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Registration not found"
        )

        RegistrationInvalid -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Invalid request"
        )

        DuplicateParticipant -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Invalid request"
        )

        EventRegistrationNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Event registration not found"
        )

        RegistrationClosed -> ApiError(
            status = HttpStatusCode.Forbidden,
            message = "Registration already closed"
        )

        RatingCategoryMissing -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Rating category is missing. This competition requires a rating category."
        )

        ParticipantOutOfAgeRestriction -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "A participant is outside of the age restriction of the rating category"
        )

        RoundAlreadyExisting -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "A round has already been created."
        )
    }
}