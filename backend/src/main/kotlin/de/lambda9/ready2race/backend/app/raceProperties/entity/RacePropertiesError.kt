package de.lambda9.ready2race.backend.app.raceProperties.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*
import java.util.*

sealed interface RacePropertiesError : ServiceError {
    data object RaceCategoryUnknown : RacePropertiesError

    data class NamedParticipantsUnknown(val namedParticipants: List<UUID>) : RacePropertiesError

    override fun respond(): ApiError = when (this) {
        RaceCategoryUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Referenced raceCategory unknown"
        )

        is NamedParticipantsUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "${"referenced namedParticipant".count(namedParticipants.size)} unknown",
            details = mapOf("unknownIds" to namedParticipants)
        )
    }
}