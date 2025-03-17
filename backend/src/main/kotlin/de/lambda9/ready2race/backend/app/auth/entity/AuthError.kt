package de.lambda9.ready2race.backend.app.auth.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class AuthError : ServiceError {

    CredentialsIncorrect,
    TokenInvalid,
    PrivilegeMissing;

    override fun respond(): ApiError = when (this) {
        CredentialsIncorrect -> ApiError(status = HttpStatusCode.Unauthorized, message = "Incorrect credentials")
        TokenInvalid -> ApiError(status = HttpStatusCode.Unauthorized, message = "Invalid session token")
        PrivilegeMissing -> ApiError(
            status = HttpStatusCode.Forbidden,
            message = "Missing privilege",
        )
    }
}