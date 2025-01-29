package de.lambda9.ready2race.backend.app.raceTemplate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateRequest
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*


// todo: Specific rights?
fun Route.raceTemplate(){
    route("/raceTemplate") {
        post {
            val params = call.receive<RaceTemplateRequest>()

            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    RaceTemplateService.addRaceTemplate(params, user.id!!)
                }}
        }
        get{
            //todo
        }

        route("/{raceTemplateId}"){
            put {
                // todo
            }
            delete{
                // todo
            }
        }
    }
}