package de.lambda9.ready2race.backend.app.competitionDeregistration.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*


fun Route.competitionDeregistration() {
    route("/deregistration") {
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateRegistrationGlobal)
                val competitionId = !pathParam("competitionId", uuid)
                val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)

                val body = !receiveKIO(CompetitionDeregistrationRequest.example)
                CompetitionDeregistrationService.createCompetitionDeregistration(
                    user.id!!,
                    competitionId,
                    competitionRegistrationId,
                    body,
                )
            }
        }

        delete {
            call.respondComprehension {
                !authenticate(Privilege.UpdateRegistrationGlobal)
                val competitionId = !pathParam("competitionId", uuid)
                val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)

                CompetitionDeregistrationService.removeCompetitionDeregistration(
                    competitionId,
                    competitionRegistrationId
                )
            }
        }
    }
}