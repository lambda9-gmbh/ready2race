package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionsOrTemplatesContainingNamedParticipant
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

sealed interface NamedParticipantError : ServiceError {
    data object NamedParticipantNotFound : NamedParticipantError

    data class NamedParticipantIsInUse(val competitionsOrTemplates: CompetitionsOrTemplatesContainingNamedParticipant) :
        NamedParticipantError

    override fun respond(): ApiError = when (this) {
        NamedParticipantNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "NamedParticipant not Found"
        )

        is NamedParticipantIsInUse -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "NamedParticipant is contained in " +
                if (competitionsOrTemplates.competitions != null) {
                    "competition".count(competitionsOrTemplates.competitions.size) +
                        if (competitionsOrTemplates.templates != null) {
                            " and "
                        } else {
                            ""
                        }
                } else {
                    ""
                } +
                if (competitionsOrTemplates.templates != null) {
                    "templates".count(competitionsOrTemplates.templates.size)
                } else {
                    ""
                },
            details = mapOf("entitiesContainingNamedParticipants" to competitionsOrTemplates)
        )
    }
}