package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.*

sealed interface CompetitionPropertiesError : ServiceError {
    data object CompetitionCategoryUnknown : CompetitionPropertiesError

    data class NamedParticipantsUnknown(val namedParticipants: List<UUID>) : CompetitionPropertiesError

    data class FeesUnknown(val fees: List<UUID>) : CompetitionPropertiesError

    override fun respond(): ApiError = when (this) {
        CompetitionCategoryUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Referenced competitionCategory unknown"
        )

        is NamedParticipantsUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "${"referenced namedParticipant".count(namedParticipants.size)} unknown",
            details = mapOf("unknownIds" to namedParticipants)
        )

        is FeesUnknown -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "${"referenced fee".count(fees.size)} unknown",
            details = mapOf("unknownIds" to fees)
        )
    }
}