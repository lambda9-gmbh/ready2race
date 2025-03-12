package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantRequest
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*
import java.util.*

fun Route.namedParticipant() {
    route("/namedParticipant") {
        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)

                val body = !receiveKIO(NamedParticipantRequest.example)
                NamedParticipantService.addNamedParticipant(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val params = !pagination<NamedParticipantSort>()
                NamedParticipantService.page(params)
            }
        }

        route("/{namedParticipantId}"){
            put{
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val namedParticipantId = !pathParam("namedParticipantId") { UUID.fromString(it) }

                    val body = !receiveKIO(NamedParticipantRequest.example)
                    NamedParticipantService.updateNamedParticipant(namedParticipantId, body, user.id!!)
                }
            }

            delete{
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val namedParticipantId = !pathParam("namedParticipantId") { UUID.fromString(it) }
                    NamedParticipantService.deleteNamedParticipant(namedParticipantId)
                }
            }
        }
    }
}