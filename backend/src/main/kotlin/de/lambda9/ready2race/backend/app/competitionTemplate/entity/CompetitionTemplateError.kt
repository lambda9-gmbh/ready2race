package de.lambda9.ready2race.backend.app.competitionTemplate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*
import java.util.*


sealed interface CompetitionTemplateError : ServiceError {
    data object CompetitionTemplateNotFound : CompetitionTemplateError
    data object CompetitionPropertiesNotFound : CompetitionTemplateError
    data object CompetitionCategoryUnknown : CompetitionTemplateError

    data class NamedParticipantsUnknown(val namedParticipants: List<UUID>) : CompetitionTemplateError

    override fun respond(): ApiError = when (this) {
        is CompetitionTemplateNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "CompetitionTemplate not found"
        )

        is CompetitionPropertiesNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "No associated competitionProperties found for the competitionTemplate"
        )

        is CompetitionCategoryUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Referenced competitionCategory unknown"
        )


        is NamedParticipantsUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "${"referenced namedParticipants".count(namedParticipants.size)} unknown",
            details = mapOf("unknownIds" to namedParticipants)
        )
    }
}