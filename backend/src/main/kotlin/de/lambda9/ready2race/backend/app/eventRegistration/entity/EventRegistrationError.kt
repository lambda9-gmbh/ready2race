package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

enum class EventRegistrationError : ServiceError {
    EventNotFound;

    override fun respond(): ApiError = when (this) {
        EventNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Event not found")
    }
}