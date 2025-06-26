package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantRequest
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.namedParticipant() {
    route("/namedParticipant") {
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(NamedParticipantRequest.example)
                NamedParticipantService.addNamedParticipant(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<NamedParticipantSort>()
                NamedParticipantService.page(params)
            }
        }

        route("/{namedParticipantId}"){
            put{
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val namedParticipantId = !pathParam("namedParticipantId", uuid)

                    val body = !receiveKIO(NamedParticipantRequest.example)
                    NamedParticipantService.updateNamedParticipant(namedParticipantId, body, user.id!!)
                }
            }

            delete{
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val namedParticipantId = !pathParam("namedParticipantId", uuid)
                    NamedParticipantService.deleteNamedParticipant(namedParticipantId)
                }
            }
        }
    }
}