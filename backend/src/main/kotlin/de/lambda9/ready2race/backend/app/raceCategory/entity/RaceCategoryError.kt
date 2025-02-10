package de.lambda9.ready2race.backend.app.raceCategory.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

enum class RaceCategoryError : ServiceError {
    RaceCategoryNotFound;

    override fun respond(): ApiError = when (this) {
        RaceCategoryNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "RaceCategory not Found"
        )
    }
}