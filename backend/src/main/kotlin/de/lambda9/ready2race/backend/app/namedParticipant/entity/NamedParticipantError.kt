package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionsOrTemplatesContainingReference
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface NamedParticipantError : ServiceError {
    data object NotFound : NamedParticipantError

    data class NamedParticipantInUse(
        val competitionsOrTemplates: CompetitionsOrTemplatesContainingReference
    ) : NamedParticipantError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "NamedParticipant not found"
        )

        is NamedParticipantInUse -> ApiError(
            status = HttpStatusCode.Conflict,
            message = competitionsOrTemplates.errorMessage("NamedParticipant"),
            details = mapOf("entitiesContainingNamedParticipant" to competitionsOrTemplates)
        )
    }
}