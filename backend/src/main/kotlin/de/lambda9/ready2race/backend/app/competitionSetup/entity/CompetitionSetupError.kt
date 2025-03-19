package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface CompetitionSetupError : ServiceError {
    data object NotFound : CompetitionSetupError
    data object CompetitionPropertiesNotFound: CompetitionSetupError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "CompetitionSetupError not found")
        CompetitionPropertiesNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "CompetitionProperties not found")
    }
}