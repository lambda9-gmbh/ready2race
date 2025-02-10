package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*
import java.util.*

sealed interface RaceError : ServiceError {
    data object RaceNotFound : RaceError
    data object RacePropertiesNotFound : RaceError
    data object RaceTemplateUnknown : RaceError

    data class ReferencedDaysUnknown(val days: List<UUID>) : RaceError

    override fun respond(): ApiError = when (this) {
        RaceNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Race not found"
        )

        RacePropertiesNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "No associated raceProperties found for the race"
        )

        RaceTemplateUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Referenced raceTemplate is unknown"
        )

        is ReferencedDaysUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "${"referenced race".count(days.size)} unknown",
            details = mapOf("unknownIds" to days)
        )
    }
}