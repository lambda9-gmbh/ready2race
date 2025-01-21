package de.lambda9.ready2race.backend.app.race.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.app.race.entity.RaceWithPropertiesSort
import de.lambda9.ready2race.backend.plugins.authenticate
import de.lambda9.ready2race.backend.plugins.pagination
import de.lambda9.ready2race.backend.plugins.pathParam
import de.lambda9.ready2race.backend.plugins.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

fun Route.race() {
    route("/race") {

        post {
            val params = call.receive<RaceRequest>()
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.EVENT_EDIT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    RaceService.addRace(params, user.id!!, eventId)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.EVENT_VIEW)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    val params = !pagination<RaceWithPropertiesSort>()
                    RaceService.pageWithPropertiesByEvent(eventId, params)
                }
            }
        }

        route("/{raceId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.EVENT_VIEW)
                        val raceId = !pathParam("raceId") { UUID.fromString(it) }
                        RaceService.getRaceWithPropertiesById(raceId)
                    }
                }
            }

            put {
                val params = call.receive<RaceRequest>()
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.EVENT_EDIT)
                        val raceId = !pathParam("raceId") { UUID.fromString(it) }
                        RaceService.updateRace(params, user.id!!, raceId)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.EVENT_EDIT)
                        val id = !pathParam("raceId") { UUID.fromString(it) }
                        RaceService.deleteRace(id)
                    }
                }
            }
        }
    }
}