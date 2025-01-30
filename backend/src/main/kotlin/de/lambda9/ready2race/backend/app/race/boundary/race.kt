package de.lambda9.ready2race.backend.app.race.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.race.entity.AssignDaysToRaceRequest
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.app.race.entity.RaceWithPropertiesSort
import de.lambda9.ready2race.backend.requests.*
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

fun Route.race() {
    route("/race") {

        post {
            val params = call.receiveV(RaceRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    RaceService.addRace(params.getOrThrow(), user.id!!, eventId)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    val params = !pagination<RaceWithPropertiesSort>()
                    val eventDayId = !optionalQueryParam("eventDayId") { UUID.fromString(it) }

                    RaceService.pageWithPropertiesByEvent(eventId, params, eventDayId)
                }
            }
        }

        route("/{raceId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                        val raceId = !pathParam("raceId") { UUID.fromString(it) }
                        RaceService.getRaceWithProperties(raceId)
                    }
                }
            }

            put {
                val params = call.receiveV(RaceRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val raceId = !pathParam("raceId") { UUID.fromString(it) }
                        RaceService.updateRace(params.getOrThrow(), user.id!!, raceId)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val raceId = !pathParam("raceId") { UUID.fromString(it) }
                        RaceService.deleteRace(raceId)
                    }
                }
            }

            route("/days"){

                put {
                    val params = call.receive<AssignDaysToRaceRequest>()
                    call.respondKIO {
                        KIO.comprehension {
                            val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                            val raceId = !pathParam("raceId") { UUID.fromString(it) }
                            RaceService.updateEventDayHasRace(params, user.id!!, raceId)
                        }
                    }
                }
            }
        }
    }
}