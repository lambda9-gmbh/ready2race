package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantRequest
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.namedParticipant() {
    route("/namedParticipant") {
        post {
            val payload = call.receiveV(NamedParticipantRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    NamedParticipantService.addNamedParticipant(!payload, user.id!!)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val params = !pagination<NamedParticipantSort>()
                    NamedParticipantService.page(params)
                }
            }
        }

        route("/{namedParticipantId}"){
            put{
                val payload = call.receiveV(NamedParticipantRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val namedParticipantId = !pathParam("namedParticipantId") { UUID.fromString(it) }
                        NamedParticipantService.updateNamedParticipant(namedParticipantId, !payload, user.id!!)
                    }
                }
            }

            delete{
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val namedParticipantId = !pathParam("namedParticipantId") { UUID.fromString(it) }
                        NamedParticipantService.deleteNamedParticipant(namedParticipantId)
                    }
                }
            }
        }
    }
}