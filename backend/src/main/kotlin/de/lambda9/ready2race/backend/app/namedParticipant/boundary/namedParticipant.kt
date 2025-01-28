package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.namedParticipant() {
    route("/namedParticipant") {
        post {
            val params = call.receive<NamedParticipantDto>()
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT) // todo: Other rights?
                    NamedParticipantService.addNamedParticipant(params)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT) // todo: Other rights?
                    NamedParticipantService.getNamedParticipantList()
                }
            }
        }

        route("/{name}"){
            put{
                val params = call.receive<NamedParticipantDto>()
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT) // todo: Other rights?
                        val prevName = !pathParam("name") { it }
                        NamedParticipantService.updateNamedParticipant(params, prevName)
                    }
                }
            }

            delete{
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT) // todo: Other rights?
                        val prevName = !pathParam("name") { it }
                        NamedParticipantService.deleteNamedParticipant(prevName)
                    }
                }
            }
        }
    }
}