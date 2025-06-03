package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class CompetitionExecutionError: ServiceError {
    NoSetupRoundFound;

    override fun respond(): ApiError = when (this) {
        NoSetupRoundFound -> ApiError(status = HttpStatusCode.BadRequest, message = "No setup round found")
    }
}