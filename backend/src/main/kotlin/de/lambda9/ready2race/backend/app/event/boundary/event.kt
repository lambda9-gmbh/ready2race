package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.boundary.competition
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.app.eventDay.boundary.eventDay
import de.lambda9.ready2race.backend.app.eventDocument.boundary.eventDocument
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.event() {
    route("/event") {

        post {
            val payload = call.receiveV(EventRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)

                    val body = !payload
                    EventService.addEvent(body, user.id!!)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val params = !pagination<EventSort>()
                    EventService.page(params)
                }
            }
        }

        route("/{eventId}") {

            eventDay()
            competition()
            eventDocument()

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                        val id = !pathParam("eventId") { UUID.fromString(it) }
                        EventService.getEvent(id)
                    }
                }
            }

            put {
                val payload = call.receiveV(EventRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val id = !pathParam("eventId") { UUID.fromString(it) }

                        val body = !payload
                        EventService.updateEvent(body, user.id!!, id)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val id = !pathParam("eventId") { UUID.fromString(it) }
                        EventService.deleteEvent(id)
                    }
                }
            }
        }
    }
}