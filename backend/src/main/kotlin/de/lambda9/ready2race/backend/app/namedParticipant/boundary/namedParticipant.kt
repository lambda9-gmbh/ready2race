package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.plugins.authenticate
import de.lambda9.ready2race.backend.plugins.pathParam
import de.lambda9.ready2race.backend.plugins.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.namedParticipant() {
    route("/namedParticipant") {
        post {
            val params = call.receive<NamedParticipantDto>()
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.EVENT_EDIT) // todo: Other rights?
                    NamedParticipantService.addNamedParticipant(params)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.EVENT_VIEW) // todo: Other rights?
                    NamedParticipantService.getNamedParticipantList()
                }
            }
        }

        route("/{name}"){
            put{
                val params = call.receive<NamedParticipantDto>()
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.EVENT_EDIT) // todo: Other rights?
                        val prevName = !pathParam("name") { it }
                        NamedParticipantService.updateNamedParticipant(params, prevName)
                    }
                }
            }

            delete{
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.EVENT_EDIT) // todo: Other rights?
                        val prevName = !pathParam("name") { it }
                        NamedParticipantService.deleteNamedParticipant(prevName)
                    }
                }
            }
        }
    }
}