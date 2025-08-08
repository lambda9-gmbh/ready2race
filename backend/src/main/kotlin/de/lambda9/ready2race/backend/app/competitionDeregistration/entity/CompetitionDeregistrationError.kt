package de.lambda9.ready2race.backend.app.competitionDeregistration.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class CompetitionDeregistrationError : ServiceError {
    NotFound,
    AlreadyExists,
    IsLocked;

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "CompetitionDeregistration not found")
        AlreadyExists -> ApiError(status = HttpStatusCode.Conflict, message = "CompetitionDeregistration already exists")
        IsLocked -> ApiError(status = HttpStatusCode.Conflict, message = "CompetitionDeregistration was created before this round and is therefore locked")
    }
}