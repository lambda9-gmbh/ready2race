package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

enum class AppUserError : ServiceError {

    EmailAlreadyInUse,
    NotFound,
    RegistrationNotFound,
    InvitationNotFound,
    PasswordResetNotFound,
    ClubNameAlreadyExists;

    override fun respond(): ApiError = when (this) {
        EmailAlreadyInUse ->
            ApiError(
                status = HttpStatusCode.Conflict,
                message = "Email already in use",
                errorCode = ErrorCode.EMAIL_IN_USE
            )

        NotFound ->
            ApiError(
                status = HttpStatusCode.NotFound,
                message = "User not found",
            )

        RegistrationNotFound ->
            ApiError(
                status = HttpStatusCode.NotFound,
                message = "Registration not found",
            )

        InvitationNotFound ->
            ApiError(
                status = HttpStatusCode.NotFound,
                message = "Invitation not found",
            )

        PasswordResetNotFound ->
            ApiError(
                status = HttpStatusCode.NotFound,
                message = "Reset password request not found",
            )

        ClubNameAlreadyExists ->
            ApiError(
                status = HttpStatusCode.Conflict,
                message = "Club name already exists",
                errorCode = ErrorCode.CLUB_NAME_ALREADY_EXISTS
            )
    }
}