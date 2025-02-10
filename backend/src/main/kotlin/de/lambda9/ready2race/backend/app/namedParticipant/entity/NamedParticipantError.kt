package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacesOrTemplatesContainingNamedParticipant
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

sealed interface NamedParticipantError : ServiceError {
    data object NamedParticipantNotFound : NamedParticipantError

    data class NamedParticipantIsInUse(val racesOrTemplates: RacesOrTemplatesContainingNamedParticipant) :
        NamedParticipantError

    override fun respond(): ApiError = when (this) {
        NamedParticipantNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "NamedParticipant not Found"
        )

        is NamedParticipantIsInUse -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "NamedParticipant is contained in " +
                if (racesOrTemplates.races != null) {
                    "race".count(racesOrTemplates.races.size) +
                        if (racesOrTemplates.templates != null) {
                            " and "
                        } else {
                            ""
                        }
                } else {
                    ""
                } +
                if (racesOrTemplates.templates != null) {
                    "templates".count(racesOrTemplates.templates.size)
                } else {
                    ""
                },
            details = mapOf("entitiesContainingNamedParticipants" to racesOrTemplates)
        )
    }
}