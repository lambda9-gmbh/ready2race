package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface EventRegistrationError : ServiceError {
    data object NotFound : EventRegistrationError
    data object EventNotFound : EventRegistrationError
    data object RegistrationClosed : EventRegistrationError
    data object RegistrationsNotFinalized : EventRegistrationError

    data object NoWizardInChallengeMode : EventRegistrationError

    data class InvalidRegistration(val msg: String) : EventRegistrationError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Registration not Found")
        is InvalidRegistration -> ApiError(status = HttpStatusCode.BadRequest, message = "Invalid registration: $msg")
        EventNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Event not found")
        RegistrationClosed -> ApiError(status = HttpStatusCode.Forbidden, message = "Registration closed")
        RegistrationsNotFinalized -> ApiError(status = HttpStatusCode.BadRequest, message = "Event not finalized")
        NoWizardInChallengeMode -> ApiError(status = HttpStatusCode.Forbidden, message = "No Registration Wizard In Challenge Mode")
    }
}