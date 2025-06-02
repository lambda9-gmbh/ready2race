package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.competitionExecution(){
    route("/createNextRound"){
        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                val competitionId = !pathParam("competitionId", uuid)

                CompetitionExecutionService.createNewRound(competitionId, user.id!!)
            }
        }
    }
}