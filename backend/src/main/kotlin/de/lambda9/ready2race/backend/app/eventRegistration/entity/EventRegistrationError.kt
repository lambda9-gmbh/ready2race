package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class EventRegistrationError : ServiceError {
    EventNotFound,
    InvalidRegistration,
    RegistrationsNotFinalized;

    override fun respond(): ApiError = when (this) {
        EventNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Event not found")
        InvalidRegistration -> ApiError(status = HttpStatusCode.BadRequest, message = "Invalid registration")
        RegistrationsNotFinalized -> ApiError(status = HttpStatusCode.BadRequest, message = "Event not finalized")
    }
}