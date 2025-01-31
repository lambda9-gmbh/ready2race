package de.lambda9.ready2race.backend.app.raceTemplate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateRequest
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateWithPropertiesSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*


// todo: Specific rights?
fun Route.raceTemplate() {
    route("/raceTemplate") {
        post {
            val payload = call.receiveV(RaceTemplateRequest.example)

            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    RaceTemplateService.addRaceTemplate(!payload, user.id!!)
                }
            }
        }
        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val params = !pagination<RaceTemplateWithPropertiesSort>()

                    RaceTemplateService.page(params)
                }
            }
        }

        route("/{raceTemplateId}") {
            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                        val raceTemplateId = !pathParam("raceTemplateId") { UUID.fromString(it) }
                        RaceTemplateService.getRaceTemplateWithProperties(raceTemplateId)
                    }
                }
            }
            put {
                val payload = call.receiveV(RaceTemplateRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val raceTemplateId = !pathParam("raceTemplateId") { UUID.fromString(it) }
                        RaceTemplateService.updateRaceTemplate(!payload, user.id!!, raceTemplateId)
                    }
                }
            }
            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val raceTemplateId = !pathParam("raceTemplateId") { UUID.fromString(it) }
                        RaceTemplateService.deleteRaceTemplate(raceTemplateId)
                    }
                }
            }
        }
    }
}