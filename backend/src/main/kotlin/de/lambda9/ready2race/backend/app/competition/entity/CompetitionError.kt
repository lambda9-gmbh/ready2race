package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.*

sealed interface CompetitionError : ServiceError {
    data object CompetitionNotFound : CompetitionError
    data object CompetitionPropertiesNotFound : CompetitionError
    data object CompetitionTemplateUnknown : CompetitionError

    data class ReferencedDaysUnknown(val days: List<UUID>) : CompetitionError

    override fun respond(): ApiError = when (this) {
        CompetitionNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Competition not found"
        )

        CompetitionPropertiesNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "No associated competitionProperties found for the competition"
        )

        CompetitionTemplateUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Referenced competitionTemplate is unknown"
        )

        is ReferencedDaysUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "${"referenced competition".count(days.size)} unknown",
            details = mapOf("unknownIds" to days)
        )
    }
}