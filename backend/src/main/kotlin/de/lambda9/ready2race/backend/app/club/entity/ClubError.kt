package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class ClubError : ServiceError {
    ClubNotFound;

    override fun respond(): ApiError = when (this) {
        ClubNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Club not found")
    }
}