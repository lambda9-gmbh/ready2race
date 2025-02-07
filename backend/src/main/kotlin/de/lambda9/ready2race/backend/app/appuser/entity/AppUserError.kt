package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

enum class AppUserError : ServiceError {

    EmailAlreadyInUse,
    RegistrationNotFound,
    InvitationNotFound;

    override fun respond(): ApiError = when (this) {
        EmailAlreadyInUse ->
            ApiError(
                status = HttpStatusCode.Conflict,
                message = "Email already in use",
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
    }
}