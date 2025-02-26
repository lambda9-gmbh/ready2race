package de.lambda9.ready2race.backend.app.competitionCategory.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

enum class CompetitionCategoryError : ServiceError {
    CompetitionCategoryNotFound;

    override fun respond(): ApiError = when (this) {
        CompetitionCategoryNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "CompetitionCategory not Found"
        )
    }
}