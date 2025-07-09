package de.lambda9.ready2race.backend.app.substitution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class SubstitutionError : ServiceError {
    NotFound;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Substitution not found")
    }
}