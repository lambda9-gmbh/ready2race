package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.logger
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

// todo: Specific rights?
fun Route.namedParticipant() {
    route("/namedParticipant") {
        post {
            val params = call.receiveV(NamedParticipantDto.example)
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    NamedParticipantService.addNamedParticipant(params)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    NamedParticipantService.getNamedParticipantList()
                }
            }
        }

        route("/{namedParticipantId}"){
            put{
                val params = call.receiveV(NamedParticipantDto.example)
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val namedParticipantId = !pathParam("namedParticipantId") { UUID.fromString(it) }
                        NamedParticipantService.updateNamedParticipant(params, namedParticipantId)
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