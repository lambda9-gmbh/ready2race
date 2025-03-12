package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.boundary.competition
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.app.eventDay.boundary.eventDay
import de.lambda9.ready2race.backend.app.eventDocument.boundary.eventDocument
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*
import java.util.*

fun Route.event() {
    route("/event") {

        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)

                val body = !receiveKIO(EventRequest.example)
                EventService.addEvent(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val params = !pagination<EventSort>()
                EventService.page(params)
            }
        }

        route("/{eventId}") {

            eventDay()
            competition()
            eventDocument()

            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val id = !pathParam("eventId") { UUID.fromString(it) }
                    EventService.getEvent(id)
                }
            }

            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val id = !pathParam("eventId") { UUID.fromString(it) }

                    val body = !receiveKIO(EventRequest.example)
                    EventService.updateEvent(body, user.id!!, id)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val id = !pathParam("eventId") { UUID.fromString(it) }
                    EventService.deleteEvent(id)
                }
            }
        }
    }
}