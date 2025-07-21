package de.lambda9.ready2race.backend.app.caterer.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class CatererError : ServiceError {
    UserNotFound,
    InvalidPrice;

    override fun respond(): ApiError = when (this) {
        UserNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "User not found")
        InvalidPrice -> ApiError(status = HttpStatusCode.BadRequest, message = "Price cannot be negative")
    }
}