package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.competitionExecution(){
    route("competitionExecution"){
        get{
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val competitionId = !pathParam("competitionId", uuid)

                CompetitionExecutionService.getProgress(competitionId)
            }
        }
        route("/createNextRound"){
            post {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    val competitionId = !pathParam("competitionId", uuid)

                    CompetitionExecutionService.createNewRound(competitionId, user.id!!)
                }
            }
        }
        route("currentRound"){
            put{
                // todo Update Places and Start Time
            }
        }

    }

}