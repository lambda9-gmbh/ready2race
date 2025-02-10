package de.lambda9.ready2race.backend.app.raceTemplate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*
import java.util.*


sealed interface RaceTemplateError : ServiceError {
    data object RaceTemplateNotFound : RaceTemplateError
    data object RacePropertiesNotFound : RaceTemplateError
    data object RaceCategoryUnknown : RaceTemplateError

    data class NamedParticipantsUnknown(val namedParticipants: List<UUID>) : RaceTemplateError

    override fun respond(): ApiError = when (this) {
        is RaceTemplateNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "RaceTemplate not found"
        )

        is RacePropertiesNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "No associated raceProperties found for the raceTemplate"
        )

        is RaceCategoryUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Referenced raceCategory unknown"
        )


        is NamedParticipantsUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "${"referenced namedParticipants".count(namedParticipants.size)} unknown",
            details = mapOf("unknownIds" to namedParticipants)
        )
    }
}