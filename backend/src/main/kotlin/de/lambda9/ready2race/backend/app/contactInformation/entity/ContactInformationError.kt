package de.lambda9.ready2race.backend.app.contactInformation.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class ContactInformationError : ServiceError {

    NotFound;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Contact not found",
        )
    }
}